package org.helioviewer.jhv.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.helioviewer.jhv.base.Globals;
import org.helioviewer.jhv.base.Telemetry;
import org.helioviewer.jhv.base.UILatencyWatchdog;
import org.helioviewer.jhv.base.coordinates.HeliocentricCartesianCoordinate;
import org.helioviewer.jhv.base.coordinates.HeliographicCoordinate;
import org.helioviewer.jhv.base.math.MathUtils;
import org.helioviewer.jhv.base.math.Matrix4d;
import org.helioviewer.jhv.base.math.Quaternion;
import org.helioviewer.jhv.base.math.Vector3d;
import org.helioviewer.jhv.base.physics.Constants;
import org.helioviewer.jhv.base.physics.DifferentialRotation;
import org.helioviewer.jhv.gui.statusLabels.CameraListener;
import org.helioviewer.jhv.gui.statusLabels.PanelMouseListener;
import org.helioviewer.jhv.layers.ImageLayer;
import org.helioviewer.jhv.layers.ImageLayer.PreparedImage;
import org.helioviewer.jhv.layers.LUT;
import org.helioviewer.jhv.layers.Layer;
import org.helioviewer.jhv.layers.LayerListener;
import org.helioviewer.jhv.layers.Layers;
import org.helioviewer.jhv.layers.PluginLayer;
import org.helioviewer.jhv.opengl.NoImageScreen;
import org.helioviewer.jhv.opengl.RayTrace;
import org.helioviewer.jhv.opengl.RayTrace.Ray;
import org.helioviewer.jhv.opengl.camera.Camera;
import org.helioviewer.jhv.opengl.camera.CameraInteraction;
import org.helioviewer.jhv.opengl.camera.CameraMode;
import org.helioviewer.jhv.opengl.camera.CameraMode.MODE;
import org.helioviewer.jhv.opengl.camera.CameraPanInteraction;
import org.helioviewer.jhv.opengl.camera.CameraRotationInteraction;
import org.helioviewer.jhv.opengl.camera.CameraZoomBoxInteraction;
import org.helioviewer.jhv.opengl.camera.CameraZoomInteraction;
import org.helioviewer.jhv.opengl.camera.animation.CameraAnimation;
import org.helioviewer.jhv.viewmodel.TimeLine;
import org.helioviewer.jhv.viewmodel.TimeLine.DecodeQualityLevel;
import org.helioviewer.jhv.viewmodel.TimeLine.TimeLineListener;
import org.helioviewer.jhv.viewmodel.metadata.MetaData;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.ImageUtil;
import com.jogamp.opengl.util.awt.TextRenderer;

import sun.misc.GC.LatencyRequest;

public class MainPanel extends GLCanvas implements GLEventListener, Camera
{
	public static final double MAX_DISTANCE = Constants.SUN_MEAN_DISTANCE_TO_EARTH * 2.5;
	public static final double MIN_DISTANCE = Constants.SUN_RADIUS * 1.2;
	public static final double DEFAULT_DISTANCE = 22 * Constants.SUN_RADIUS;

	public static final double CLIP_NEAR = Constants.SUN_RADIUS / 10;
	public static final double CLIP_FAR = Constants.SUN_RADIUS * 1000;
	public static final double FOV = 10;
	private double aspect = 0.0;

	private Vector3d[] visibleAreaOutline;

	protected Quaternion rotationNow;
	protected Vector3d translationNow;
	protected Quaternion rotationEnd;
	protected Vector3d translationEnd;
	private ArrayList<MainPanel> synchronizedViews;

	private ArrayList<PanelMouseListener> panelMouseListeners;
	private ArrayList<CameraListener> cameraListeners;
	private ArrayList<CameraAnimation> cameraAnimations;

	protected CameraInteraction[] cameraInteractionsLeft;
	protected CameraInteraction[] cameraInteractionsRight;
	protected CameraInteraction[] cameraInteractionsMiddle;

	private boolean cameraTrackingEnabled = false;

	private long lastCameraTrackingDate;

	private int[] frameBufferObject;
	private int[] renderBufferDepth;
	private int[] renderBufferColor;

	private static final int TILE_WIDTH = 2048;
	private static final int TILE_HEIGHT = 2048;

	public MainPanel(GLContext _context)
	{
		cameraAnimations = new ArrayList<>();
		synchronizedViews = new ArrayList<>();
		panelMouseListeners = new ArrayList<>();
		cameraListeners = new ArrayList<>();
		setSharedContext(_context);

		Layers.addLayerListener(new LayerListener()
		{
			@Override
			public void layerAdded()
			{
				repaint();
			}

			@Override
			public void layersRemoved()
			{
				repaint();
			}

			@Override
			public void activeLayerChanged(@Nullable Layer layer)
			{
				if (Layers.getActiveImageLayer() != null)
					lastCameraTrackingDate = 0;
				repaint();
			}
		});
		TimeLine.SINGLETON.addListener(new TimeLineListener()
		{
			@Override
			public void timeStampChanged(long current, long last)
			{
				MainPanel.this.repaintInternal(true);
			}
			
			@Override
			public void timeRangeChanged()
			{
			}

			@Override
			public void isPlayingChanged(boolean _isPlaying)
			{
				MainPanel.this.repaintInternal(false);
			}
		});
		addMouseListener(new MouseAdapter()
				{
					@Override
					public void mousePressed(@Nullable MouseEvent e)
					{
						if (e == null)
							return;
		
						Ray ray = new RayTrace().cast(e.getX(), e.getY(), MainPanel.this);
						if(SwingUtilities.isLeftMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsLeft)
								cameraInteraction.mousePressed(e, ray);
						else if(SwingUtilities.isRightMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsRight)
								cameraInteraction.mousePressed(e, ray);
						else if(SwingUtilities.isMiddleMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsMiddle)
								cameraInteraction.mousePressed(e, ray);
					}
		
					@Override
					public void mouseReleased(@Nullable MouseEvent e)
					{
						if (e == null)
							return;
		
						Ray ray = new RayTrace().cast(e.getX(), e.getY(), MainPanel.this);
						if(SwingUtilities.isLeftMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsLeft)
								cameraInteraction.mouseReleased(e, ray);
						else if(SwingUtilities.isRightMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsRight)
								cameraInteraction.mouseReleased(e, ray);
						else if(SwingUtilities.isMiddleMouseButton(e))
							for (CameraInteraction cameraInteraction : cameraInteractionsMiddle)
								cameraInteraction.mouseReleased(e, ray);
					}
		
					@Override
					public void mouseExited(@Nullable MouseEvent e)
					{
						for (PanelMouseListener listener : panelMouseListeners)
							listener.mouseExited();
					}
				});
		addMouseMotionListener(new MouseMotionListener()
		{
			@Override
			public void mouseDragged(@Nullable MouseEvent e)
			{
				if (e == null)
					return;

				Ray ray = new RayTrace().cast(e.getX(), e.getY(), MainPanel.this);
				if(SwingUtilities.isLeftMouseButton(e))
					for (CameraInteraction cameraInteraction : cameraInteractionsLeft)
						cameraInteraction.mouseDragged(e, ray);
				else if(SwingUtilities.isRightMouseButton(e))
					for (CameraInteraction cameraInteraction : cameraInteractionsRight)
						cameraInteraction.mouseDragged(e, ray);
				else if(SwingUtilities.isMiddleMouseButton(e))
					for (CameraInteraction cameraInteraction : cameraInteractionsMiddle)
						cameraInteraction.mouseDragged(e, ray);
			}

			@Override
			public void mouseMoved(@Nullable MouseEvent e)
			{
				if (e == null)
					return;

				Ray ray = new RayTrace().cast(e.getX(), e.getY(), MainPanel.this);
				for (PanelMouseListener listener : panelMouseListeners)
					listener.mouseMoved(e, ray);
			}
		});
		addGLEventListener(this);
		addMouseWheelListener(new MouseWheelListener()
		{
			@Override
			public void mouseWheelMoved(@Nullable MouseWheelEvent e)
			{
				if (e == null)
					return;
				
				Ray ray = new RayTrace().cast(e.getX(), e.getY(), MainPanel.this);
				for (CameraInteraction cameraInteraction : cameraInteractionsLeft)
					cameraInteraction.mouseWheelMoved(e, ray);
			}
		});

		rotationNow = rotationEnd = Quaternion.IDENTITY;
		translationNow = translationEnd = new Vector3d(0, 0, DEFAULT_DISTANCE);

		cameraInteractionsLeft = new CameraInteraction[]
				{
						new CameraZoomInteraction(this, this),
						new CameraRotationInteraction(this, this)
				};

		cameraInteractionsMiddle = new CameraInteraction[]
				{
						new CameraPanInteraction(this, this, 1)
				};
		
		cameraInteractionsRight = new CameraInteraction[]
				{
						new CameraPanInteraction(this, this, 1)
				};
		
		visibleAreaOutline = new Vector3d[40];
	}
	
	protected void repaintInternal(boolean _synchronously)
	{
		if(_synchronously)
			display();
		else
			repaint();
	}

	public Quaternion getRotationCurrent()
	{
		return rotationNow;
	}

	public Vector3d getTranslationCurrent()
	{
		return translationNow;
	}

	public Quaternion getRotationEnd()
	{
		return rotationEnd;
	}

	public Vector3d getTranslationEnd()
	{
		return translationEnd;
	}

	public void setRotationEnd(Quaternion _rotationEnd)
	{
		rotationEnd = _rotationEnd;
	}

	public void setTranslationEnd(Vector3d _translationEnd)
	{
		translationEnd = _translationEnd;
	}

	public void setRotationCurrent(Quaternion _rotationNow)
	{
		rotationNow = _rotationNow;
		repaint();
		for (CameraListener listener : cameraListeners)
			listener.cameraChanged();
	}

	public void setTranslationCurrent(Vector3d _translationNow)
	{
		if (_translationNow.isApproxEqual(translationNow, 1E-6))
			return;

		translationNow = _translationNow;
		repaint();
		for (CameraListener listener : cameraListeners)
			listener.cameraChanged();
	}

	public void activateRotationInteraction()
	{
		cameraInteractionsLeft = new CameraInteraction[]
				{
						new CameraZoomInteraction(this, this),
						new CameraRotationInteraction(this, this)
				};

		cameraInteractionsMiddle = new CameraInteraction[]
				{
						new CameraPanInteraction(this, this, 1)
				};
		
		cameraInteractionsRight = new CameraInteraction[]
				{
						new CameraPanInteraction(this, this, 1)
				};
	}

	public void activatePanInteraction()
	{
		cameraInteractionsLeft = new CameraInteraction[]
				{
						new CameraZoomInteraction(this, this),
						new CameraPanInteraction(this, this, 1)
				};

		cameraInteractionsMiddle = new CameraInteraction[]
				{
						new CameraRotationInteraction(this, this)
				};
		
		cameraInteractionsRight = new CameraInteraction[]
				{
					new CameraRotationInteraction(this, this)
				};
	}

	public void activateZoomBoxInteraction()
	{
		cameraInteractionsLeft = new CameraInteraction[]
				{
						new CameraZoomInteraction(this, this),
						new CameraZoomBoxInteraction(this, this)
				};

		cameraInteractionsMiddle = new CameraInteraction[]
				{
					new CameraPanInteraction(this, this, 1)
				};
		
		cameraInteractionsRight = new CameraInteraction[]
				{
						new CameraRotationInteraction(this, this)
				};
	}
	
	protected void render(GL2 gl, boolean _showLoadingAnimation, Dimension sizeForDecoder)
	{
		gl.glClearDepth(1);
		gl.glDepthMask(true);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		gl.glDepthMask(false);

		boolean anyImageLayerVisible = false;
		for (Layer layer : Layers.getLayers())
			if (layer instanceof ImageLayer)
				anyImageLayerVisible |= layer.isVisible();
		
		double clipNear = MathUtils.clip(translationNow.z - 4 * Constants.SUN_RADIUS, CLIP_NEAR, Math.nextDown(CLIP_FAR));
		double clipFar = MathUtils.clip(translationNow.z + 4 * Constants.SUN_RADIUS, Math.nextUp(clipNear), CLIP_FAR);
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		{
			gl.glOrtho(-1, 1, -1, 1, clipNear, translationNow.z + 4 * Constants.SUN_RADIUS);
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glLoadIdentity();

			gl.glTranslated(0, 0, -translationNow.z);
			
			//keep camera always pointed correctly in 2d mode
			if (CameraMode.mode == MODE.MODE_2D)
			{
				ImageLayer il = Layers.getActiveImageLayer();
				if (il != null)
				{
					MetaData md = il.getCurrentMetaData();
					if (md != null)
						rotationNow = md.rotation.inversed();
				}
			}
			
			ImageLayer.ensureAppropriateTextureCacheSize(gl);
			
			//TODO: jumpstart decoding of overview region
			//TODO: async texture upload
			//TODO: jumpstart decoding of next frame
			
			ArrayList<ImageLayer> imageLayers = new ArrayList<ImageLayer>();
			ArrayList<ListenableFuture<PreparedImage>> prepared = new ArrayList<>();
			for (Layer layer : Layers.getLayers())
				if (layer.isVisible() && layer instanceof ImageLayer)
				{
					ImageLayer il=(ImageLayer)layer;
					
					if(il.getLUT()!=null && il.getCurrentTimeMS()!=0) //make sure that there's a real frame for the current frame
					{
						prepared.add(il.prepareImageData(this, shouldHurry(), sizeForDecoder, gl.getContext()));
						imageLayers.add(il);
					}
				}

			
			double maxScaling = 1;
			for(int opacityGroup:MetaData.OPACITY_GROUPS)
			{
				double rSum=0;
				double gSum=0;
				double bSum=0;
				for (ImageLayer il : imageLayers)
					if((il.getGroupForOpacity() & opacityGroup) != 0)
					{
						LUT lut=il.getLUT();
						if(il.redChannel)
							rSum += lut.getAvgColor().getRed(); //*il.opacity; //<-- possible alternative
						if(il.greenChannel)
							gSum += lut.getAvgColor().getGreen(); //*il.opacity; //<-- possible alternative
						if(il.blueChannel)
							bSum += lut.getAvgColor().getBlue(); //*il.opacity; //<-- possible alternative
					}
				
				maxScaling = Math.min(maxScaling, 255d/rSum);
				maxScaling = Math.min(maxScaling, 255d/gSum);
				maxScaling = Math.min(maxScaling, 255d/bSum);
			}

			for (ListenableFuture<PreparedImage> l : Futures.inCompletionOrder(prepared))
				try
				{
					UILatencyWatchdog.setFocus(Globals.GL_WORKER_THREADS.toArray(new Thread[0]));
					PreparedImage pr = l.get();
					UILatencyWatchdog.setFocus();
					if (pr != null)
						pr.layer.renderLayer(gl, this, pr, (float)maxScaling);
				}
				catch (ExecutionException | InterruptedException _e)
				{
					Telemetry.trackException(_e);
				}
			
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glLoadIdentity();
		}
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		
		Quaternion rotation = new Quaternion(rotationNow.a, rotationNow.u.negatedY());
		Matrix4d transformation = rotation.toMatrix().translated(-translationNow.x, translationNow.y,-translationNow.z);
		gl.glMultMatrixd(transformation.m, 0);

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		
		if (CameraMode.mode == CameraMode.MODE.MODE_3D)
		{
			new GLU().gluPerspective(MainPanel.FOV, aspect, clipNear, clipFar);
		}
		else
		{
			double width = Math.tan(Math.toRadians(FOV) / 2) * translationNow.z;
			gl.glOrtho(-width, width, -width, width, clipNear, clipFar);
			gl.glScaled(1 / aspect, 1, 1);
		}

		if(aspect>1)
			gl.glScaled(aspect, aspect, 1);
		
		
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		calculateBounds();
		for (CameraInteraction cameraInteraction : cameraInteractionsLeft)
			cameraInteraction.renderInteraction(gl);
		for (CameraInteraction cameraInteraction : cameraInteractionsRight)
			cameraInteraction.renderInteraction(gl);
		for (CameraInteraction cameraInteraction : cameraInteractionsMiddle)
			cameraInteraction.renderInteraction(gl);
		
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LESS);
		gl.glDepthMask(false);
		
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		{
			//render plugin layers
			if(anyImageLayerVisible)
				for (Layer layer : Layers.getLayers())
					if (layer.isVisible() && layer instanceof PluginLayer)
						((PluginLayer)layer).renderLayer(gl,this);
		}
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		
		gl.glDepthMask(false);
		
		if (!anyImageLayerVisible && _showLoadingAnimation)
		{
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glScaled(aspect > 1 ? 1 / aspect : 1, aspect < 1 ? aspect : 1, 1);
			
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glPushMatrix();
			{
				gl.glLoadIdentity();
				
				double size = Math.max(getSurfaceHeight(), getSurfaceWidth()) * 0.15;
				gl.glViewport((int) (getSurfaceWidth() / 2 - size / 2), (int) (getSurfaceHeight() / 2 - size / 2), (int)size, (int)size);
				
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glPushMatrix();
				{
					gl.glLoadIdentity();
					NoImageScreen.render(gl);
				}
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glPopMatrix();
			}
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glPopMatrix();
		}
		
		
		//visualize depth buffer for debugging purposes
		/*if (!Globals.isReleaseVersion())
		{
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glScaled(aspect > 1 ? 1 / aspect : 1, aspect < 1 ? aspect : 1, 1);
			gl.glMatrixMode(GL2.GL_MODELVIEW);			

			gl.glEnable(GL2.GL_TEXTURE_2D);
			gl.glActiveTexture(GL2.GL_TEXTURE0);
			gl.glDisable(GL2.GL_BLEND);
			gl.glDisable(GL2.GL_TEXTURE_2D);
			gl.glEnable(GL2.GL_FRAGMENT_PROGRAM_ARB);
			gl.glEnable(GL2.GL_VERTEX_PROGRAM_ARB);
			gl.glUseProgram(0);
			gl.glDisable(GL2.GL_FRAGMENT_PROGRAM_ARB);
			gl.glDisable(GL2.GL_VERTEX_PROGRAM_ARB);

			gl.glEnable(GL2.GL_BLEND);
			gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE);
			gl.glEnable(GL2.GL_TEXTURE_2D);
			gl.glColor4d(1, 1, 1, 1);
			gl.glEnable(GL2.GL_DEPTH_TEST);

			if(tmp[0]==0)
			gl.glGenTextures(1, tmp, 0);
			gl.glBindTexture(GL2.GL_TEXTURE_2D, tmp[0]);
			gl.glCopyTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_DEPTH_COMPONENT24, 0, 0, this.getSurfaceWidth(),
					this.getSurfaceHeight(), 0);

			gl.glDisable(GL2.GL_DEPTH_TEST);
			gl.glDisable(GL2.GL_BLEND);
			gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
			gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);

			gl.glDepthMask(true);

			gl.glDisable(GL2.GL_BLEND);

			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glPushMatrix();
			gl.glOrtho(0, 1, 0, 1, 10, -10);
			gl.glViewport(0, 0, (int) (this.getSurfaceWidth() * 0.3), (int) (this.getSurfaceHeight() * 0.3));
			gl.glMatrixMode(GL2.GL_MODELVIEW);
			gl.glLoadIdentity();
			gl.glBegin(GL2.GL_QUADS);
			gl.glTexCoord2f(0, 0);
			gl.glVertex2d(0, 1);
			gl.glTexCoord2f(1, 0);
			gl.glVertex2d(1, 1);
			gl.glTexCoord2f(1, 1);
			gl.glVertex2d(1, 0);
			gl.glTexCoord2f(0, 1);
			gl.glVertex2d(0, 0);
			gl.glEnd();
			gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
			gl.glDisable(GL2.GL_TEXTURE_2D);
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glPopMatrix();
			gl.glMatrixMode(GL2.GL_MODELVIEW);
		}*/
		
		
		if(!Globals.IS_RELEASE_VERSION)
		{
			for(;;)
			{
				int glError = gl.glGetError();
				if(glError==GL2.GL_NO_ERROR)
					break;
				
				System.err.println("OpenGL error "+Integer.toHexString(glError));
			}
		}
		
		for (MainPanel componentView : synchronizedViews)
			componentView.repaint(); //display breaks OS X
	}
	
	protected DecodeQualityLevel shouldHurry()
	{
		if(!cameraAnimations.isEmpty())
			return DecodeQualityLevel.HURRY;
		
		return TimeLine.SINGLETON.shouldHurry();
	}
	
	//int tmp[] = new int[1];

	@Override
	public void display(@Nullable GLAutoDrawable _drawable)
	{
		if(_drawable==null)
			return;
		
		while (!cameraAnimations.isEmpty() && cameraAnimations.get(0).isFinished())
			cameraAnimations.remove(0);

		if (!cameraAnimations.isEmpty())
		{
			for (CameraAnimation ca : cameraAnimations)
				ca.animate(this);

			repaint();
		}
		
		if (cameraTrackingEnabled)
			updateTrackRotation();
		
		Dimension sizeForDecoder = getCanavasSize();
		switch(shouldHurry())
		{
			case QUALITY:
				break;
			case PLAYBACK:
			case SPEED:
			case HURRY:
				double scale=80d/Toolkit.getDefaultToolkit().getScreenResolution();
				sizeForDecoder = new Dimension((int)(sizeForDecoder.width * scale), (int)(sizeForDecoder.height * scale));
				break;
			default:
				throw new RuntimeException();
		}
		
		GL2 gl = _drawable.getGL().getGL2();

		gl.glViewport(0, 0, getSurfaceWidth(), getSurfaceHeight());

		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		if(aspect>=1)
			gl.glScaled(1, aspect, 1);
		else
			gl.glScaled(1/aspect, 1, 1);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		render(gl, true, sizeForDecoder);
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
	}

	protected void updateTrackRotation()
	{
		if (lastCameraTrackingDate == 0)
			lastCameraTrackingDate = TimeLine.SINGLETON.getCurrentFrameMiddleTimeMS();

		if (lastCameraTrackingDate!=TimeLine.SINGLETON.getCurrentFrameMiddleTimeMS())
		{
			long differenceMS = TimeLine.SINGLETON.getCurrentFrameMiddleTimeMS()-lastCameraTrackingDate;
			lastCameraTrackingDate = TimeLine.SINGLETON.getCurrentFrameMiddleTimeMS();
			
			RayTrace rayTrace = new RayTrace();
			Vector3d hitPoint = rayTrace.cast(getWidth() / 2, getHeight() / 2, this).getHitpoint();
			HeliographicCoordinate newCoord = new HeliocentricCartesianCoordinate(hitPoint.x, hitPoint.y, hitPoint.z).toHeliographicCoordinate();
			double angle = DifferentialRotation.calculateRotationInRadians(newCoord.latitude, differenceMS/1000d);
			
			Quaternion newRotation = Quaternion.createRotation(angle, new Vector3d(0, 1, 0)).rotated(rotationNow);
			if (CameraMode.mode == MODE.MODE_3D)
			{
				rotationEnd = Quaternion.createRotation(angle, new Vector3d(0, 1, 0)).rotated(rotationEnd);
				rotationNow = newRotation;
			}
			else
			{
				//TODO: tracking is jerky in 2d mode, should use ImageLayer.calcTransformation(mainPanel, md) instead, to respect metadata
				Vector3d newTranslation = newRotation.toMatrix().multiply(hitPoint);
				translationEnd = translationEnd.add(new Vector3d(newTranslation.x-translationNow.x,newTranslation.y-translationNow.y,0));
				translationNow = new Vector3d(newTranslation.x, newTranslation.y, translationNow.z);
			}
			
			for (CameraListener listener : cameraListeners)
				listener.cameraChanged();
		}
	}

	protected void calculateBounds()
	{
		RayTrace rayTrace = new RayTrace();
		double width = getWidth() / 10d;
		double height = getHeight() / 10d;

		for (int i = 0; i < 10; i++)
		{
			visibleAreaOutline[i   ] = rayTrace.cast((int)(i * width), 0, this).getHitpoint();
			visibleAreaOutline[i+10] = rayTrace.cast(getWidth(), (int)(i * height), this).getHitpoint();
			visibleAreaOutline[i+20] = rayTrace.cast((int)((10 - i) * width), getHeight(), this).getHitpoint();
			visibleAreaOutline[i+30] = rayTrace.cast(0, (int)((10 - i) * height), this).getHitpoint();
		}
	}

	@Override
	public void dispose(@Nullable GLAutoDrawable arg0)
	{
	}

	@Override
	public void init(@Nullable GLAutoDrawable drawable)
	{
		if(drawable==null)
			return;
		
		aspect = getSize().getWidth() / getSize().getHeight();
		GL2 gl = drawable.getGL().getGL2();
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

		gl.glDisable(GL2.GL_TEXTURE_2D);

		frameBufferObject = new int[1];
		gl.glGenFramebuffers(1, frameBufferObject, 0);
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, frameBufferObject[0]);
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		gl.glEnable(GL2.GL_TEXTURE_2D);

		gl.glDisable(GL2.GL_TEXTURE_2D);
	}

	private void generateNewRenderBuffers(GL2 gl, int width, int height)
	{
		if (renderBufferDepth != null)
			gl.glDeleteRenderbuffers(1, renderBufferDepth, 0);

		renderBufferDepth = new int[1];
		gl.glGenRenderbuffers(1, renderBufferDepth, 0);
		gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, renderBufferDepth[0]);
		gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT, width, height);
		gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT, GL2.GL_RENDERBUFFER,
				renderBufferDepth[0]);

		if (renderBufferColor != null)
			gl.glDeleteRenderbuffers(1, renderBufferColor, 0);

		renderBufferColor = new int[1];
		gl.glGenRenderbuffers(1, renderBufferColor, 0);
		gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, renderBufferColor[0]);
		gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_RGBA8, width, height);
		gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_RENDERBUFFER,
				renderBufferColor[0]);
	}

	@Override
	public void reshape(@Nullable GLAutoDrawable drawable, int x, int y, int width, int height)
	{
		aspect = Math.max(1, getSize().getWidth()) / Math.max(1, getSize().getHeight());
		repaint();
	}

	public Dimension getCanavasSize()
	{
		return new Dimension(getSurfaceWidth(), getSurfaceHeight());
	}

	public BufferedImage getBufferedImage(int imageWidth, int imageHeight, boolean textEnabled)
	{
		ArrayList<String> descriptions = null;
		if (textEnabled)
		{
			descriptions = new ArrayList<>();
			for (Layer layer : Layers.getLayers())
				if (layer.isVisible())
				{
					LocalDateTime ldt = MathUtils.toLDT(layer.getCurrentTimeMS());
					if (ldt != null)
						descriptions.add(layer.getFullName() + " - " + ldt.format(Globals.DATE_TIME_FORMATTER));
				}
		}

		int tileWidth = imageWidth < TILE_WIDTH ? imageWidth : TILE_WIDTH;
		int tileHeight = imageHeight < TILE_HEIGHT ? imageHeight : TILE_HEIGHT;
		repaint();
		double xTiles = imageWidth / (double) tileWidth;
		double yTiles = imageHeight / (double) tileHeight;
		int countXTiles = imageWidth % tileWidth == 0 ? (int) xTiles : (int) xTiles + 1;
		int countYTiles = imageHeight % tileHeight == 0 ? (int) yTiles : (int) yTiles + 1;

		GLDrawableFactory factory = GLDrawableFactory.getFactory(GLProfile.getDefault());

		GLProfile profile = GLProfile.get(GLProfile.GL2);
		GLCapabilities capabilities = new GLCapabilities(profile);
		capabilities.setDoubleBuffered(false);
		capabilities.setOnscreen(false);
		capabilities.setHardwareAccelerated(true);
		capabilities.setFBO(true);

		GLDrawable offscreenDrawable = factory.createOffscreenDrawable(null, capabilities, null, tileWidth, tileHeight);

		offscreenDrawable.setRealized(true);
		final GLContext offscreenContext = getContext();
		offscreenDrawable.setRealized(true);
		double oldAspect = aspect;
		try
		{
			offscreenContext.makeCurrent();
			GL2 offscreenGL = offscreenContext.getGL().getGL2();
	
			offscreenGL.glBindFramebuffer(GL2.GL_FRAMEBUFFER, frameBufferObject[0]);
			generateNewRenderBuffers(offscreenGL, tileWidth, tileHeight);
	
			BufferedImage screenshot = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_3BYTE_BGR);
			ByteBuffer.wrap(((DataBufferByte) screenshot.getRaster().getDataBuffer()).getData());
	
			offscreenGL.glViewport(0, 0, tileWidth, tileHeight);
			offscreenGL.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	
			aspect = imageWidth / (double) imageHeight;
			// double top = Math.tan(MainPanel.FOV / 360.0 * Math.PI) *
			// MainPanel.CLIP_NEAR;
			// double right = top * aspect;
			// double left = -right;
			// double bottom = -top;
	
			TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 24));
			textRenderer.setColor(1f, 1f, 1f, 1f);
	
			offscreenGL.glViewport(0, 0, tileWidth, tileHeight);
	
			offscreenGL.glMatrixMode(GL2.GL_PROJECTION);
			offscreenGL.glLoadIdentity();
			offscreenGL.glScaled(1, aspect, 1);
			offscreenGL.glMatrixMode(GL2.GL_MODELVIEW);
			
			//FIXME: tiling is broken
			for (int x = 0; x < countXTiles; x++)
			{
				for (int y = 0; y < countYTiles; y++)
				{
					offscreenGL.glMatrixMode(GL2.GL_PROJECTION);
					offscreenGL.glPushMatrix();
					offscreenGL.glViewport(0, 0, imageWidth, imageHeight);
					offscreenGL.glTranslated(-x, -y, 0);
					offscreenGL.glMatrixMode(GL2.GL_MODELVIEW);
	
					int destX = tileWidth * x;
					int destY = tileHeight * y;
					render(offscreenGL, false, new Dimension(imageWidth, imageHeight));
	
					if (descriptions != null && x == 0 && y == 0)
					{
						int counter = 0;
						textRenderer.beginRendering(this.getSurfaceWidth(), this.getSurfaceHeight());
						for (String description : descriptions)
							textRenderer.draw(description, 5, 5 + 40 * counter++);
	
						textRenderer.endRendering();
					}
					offscreenGL.glPixelStorei(GL2.GL_PACK_ROW_LENGTH, imageWidth);
					offscreenGL.glPixelStorei(GL2.GL_PACK_SKIP_ROWS, destY);
					offscreenGL.glPixelStorei(GL2.GL_PACK_SKIP_PIXELS, destX);
					offscreenGL.glPixelStorei(GL2.GL_PACK_ALIGNMENT, 1);
	
					int cutOffX = imageWidth >= (x + 1) * tileWidth ? tileWidth : tileWidth - x * tileWidth;
					int cutOffY = imageHeight >= (y + 1) * tileHeight ? tileHeight : tileHeight - y * tileHeight;
	
					offscreenGL.glReadPixels(0, 0, cutOffX, cutOffY, GL2.GL_BGR, GL2.GL_UNSIGNED_BYTE,
							ByteBuffer.wrap(((DataBufferByte) screenshot.getRaster().getDataBuffer()).getData()));
					offscreenGL.glMatrixMode(GL2.GL_PROJECTION);
					offscreenGL.glPopMatrix();
					offscreenGL.glMatrixMode(GL2.GL_MODELVIEW);
				}
			}
	
			ImageUtil.flipImageVertically(screenshot);
			return screenshot;
		}
		finally
		{
			offscreenContext.release();
			aspect=oldAspect;
		}
	}

	public double getAspect()
	{
		return aspect;
	}

	public void addSynchronizedView(MainPanel compenentView)
	{
		synchronizedViews.add(compenentView);
	}

	public Vector3d[] getVisibleAreaOutline()
	{
		return visibleAreaOutline;
	}

	public void addCameraAnimation(CameraAnimation cameraAnimation)
	{
		cameraAnimations.add(cameraAnimation);
		repaint();
	}
	
	private JFrame fullscreenFrame;
	private Container lastPanelParent;
	private KeyAdapter exitFullscreenListener = new KeyAdapter()
	{
		@Override
		public void keyPressed(@Nullable KeyEvent e)
		{
			if(e==null)
				return;
			
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE || (e.isAltDown() && e.getKeyCode() == KeyEvent.VK_T))
				switchToWindowed();
		}
	};
	public void switchToWindowed()
	{
		SwingUtilities.invokeLater(() ->
		{
			if(!isFullscreen())
				return;
			
			MainPanel.this.removeKeyListener(exitFullscreenListener);
			fullscreenFrame.getContentPane().remove(MainPanel.this);
			lastPanelParent.add(MainPanel.this);
			fullscreenFrame.setVisible(false);

			fullscreenFrame.dispose();
			fullscreenFrame = null;
		});
	}
	
	public boolean isFullscreen()
	{
		return fullscreenFrame != null;
	}

	public void switchToFullscreen()
	{
		SwingUtilities.invokeLater(() ->
			{
				if(isFullscreen())
					return;
				
				GraphicsDevice graphicsDevice = MainFrame.SINGLETON.getGraphicsConfiguration().getDevice();
				if (graphicsDevice == null)
					graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

				fullscreenFrame = new JFrame(graphicsDevice.getDefaultConfiguration());

				fullscreenFrame.getContentPane().setLayout(new BorderLayout());
				lastPanelParent = MainPanel.this.getParent();
				fullscreenFrame.getContentPane().add(MainPanel.this);

				fullscreenFrame.setExtendedState(Frame.MAXIMIZED_BOTH);
				fullscreenFrame.setUndecorated(true);
				fullscreenFrame.setResizable(false);

				if (graphicsDevice.isFullScreenSupported())
					graphicsDevice.setFullScreenWindow(fullscreenFrame);
				
				fullscreenFrame.setVisible(true);
				fullscreenFrame.addKeyListener(exitFullscreenListener);
				MainPanel.this.addKeyListener(exitFullscreenListener);
			});
	}
	
	public boolean isCameraTrackingEnabled()
	{
		return cameraTrackingEnabled;
	}
	
	public void setCameraTrackingEnabled(boolean _isEnabled)
	{
		if(cameraTrackingEnabled == _isEnabled)
			return;
		
		cameraTrackingEnabled = _isEnabled;
		lastCameraTrackingDate = TimeLine.SINGLETON.getCurrentFrameMiddleTimeMS();
		
		MainFrame.SINGLETON.TOP_TOOL_BAR.setTracking(_isEnabled);
	}

	public void addPanelMouseListener(PanelMouseListener _listener)
	{
		panelMouseListeners.add(_listener);
	}

	public void addCameraListener(CameraListener _listener)
	{
		cameraListeners.add(_listener);
	}

	public void abortAllAnimations()
	{
		cameraAnimations.clear();
		setTranslationEnd(getTranslationCurrent());
		setRotationEnd(getRotationCurrent());
	}
}
