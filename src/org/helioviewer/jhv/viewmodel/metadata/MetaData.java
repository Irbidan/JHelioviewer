package org.helioviewer.jhv.viewmodel.metadata;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.helioviewer.jhv.base.math.MathUtils;
import org.helioviewer.jhv.base.math.Quaternion3d;
import org.helioviewer.jhv.base.math.Vector2d;
import org.helioviewer.jhv.base.math.Vector2i;
import org.helioviewer.jhv.base.math.Vector3d;
import org.helioviewer.jhv.base.physics.Constants;
import org.helioviewer.jhv.layers.filter.LUT.LUT_ENTRY;
import org.helioviewer.jhv.viewmodel.region.PhysicalRegion;
import org.helioviewer.jhv.viewmodel.region.StaticRegion;

public abstract class MetaData {
  private Vector2d lowerLeftCorner;
    private Vector2d sizeVector;

    protected MetaDataContainer metaDataContainer = null;
    protected String instrument = "";
    protected String detector = "";
    protected String measurement = " ";
    protected String observatory = " ";
    protected String fullName = "";
    protected Vector2i pixelImageSize = new Vector2i();
    protected double solarPixelRadius = -1;
    protected Vector2d sunPixelPosition = new Vector2d();

    protected double meterPerPixel;

    protected double innerRadius;
    protected double outerRadius;
    protected double flatDistance;
    protected double maskRotation;
    protected Vector2d occulterCenter;
    protected Vector3d orientation = new Vector3d(0.00,0.00,1.00);
    private Quaternion3d defaultRotation = new Quaternion3d();
    
    protected double heeqX;
    protected double heeqY;
    protected double heeqZ;
    protected boolean heeqAvailable = false;

    protected double heeX;
    protected double heeY;
    protected double heeZ;
    protected boolean heeAvailable = false;

    protected double crlt;
    protected double crln;
    protected double dobs;
    protected boolean carringtonAvailable = false;

    protected double stonyhurstLongitude;
    protected double stonyhurstLatitude;
    protected boolean stonyhurstAvailable = false;

    
    protected boolean hasCorona = false;
    protected boolean hasSphere = false;
    protected boolean hasRotation = false;
    protected LocalDateTime localDateTime;
	protected final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE_TIME;
    
	protected LUT_ENTRY defaultLUT = LUT_ENTRY.GRAY;
	
    /**
     * Default constructor, does not set size or position.
     */
    public MetaData(MetaDataContainer metaDataContainer) {
        lowerLeftCorner = null;
        sizeVector = null;
        
        if (metaDataContainer.get("INSTRUME") == null)
            return;

        detector = metaDataContainer.get("DETECTOR");
        instrument = metaDataContainer.get("INSTRUME");

        if (detector == null) {
            detector = " ";
        }
        if (instrument == null) {
            instrument = " ";
        }

    }

    /**
     * {@inheritDoc}
     */
    public synchronized Vector2d getPhysicalImageSize() {
        return sizeVector;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Vector2d getPhysicalLowerLeft() {
        return lowerLeftCorner;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized double getPhysicalImageHeight() {
        return this.getResolution().getY() * this.getUnitsPerPixel();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized double getPhysicalImageWidth() {
        return this.getResolution().getX() * this.getUnitsPerPixel();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Vector2d getPhysicalLowerRight() {
        return lowerLeftCorner.add(sizeVector.getXVector());
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Vector2d getPhysicalUpperLeft() {
        return lowerLeftCorner.add(sizeVector.getYVector());
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Vector2d getPhysicalUpperRight() {
        return lowerLeftCorner.add(sizeVector);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized PhysicalRegion getPhysicalRegion() {
        return StaticRegion.createAdaptedRegion(lowerLeftCorner, sizeVector);
    }

    /**
     * Sets the physical size of the corresponding image.
     * 
     * @param newImageSize
     *            Physical size of the corresponding image
     */
    protected synchronized void setPhysicalImageSize(Vector2d newImageSize) {
        sizeVector = newImageSize;
    }

    /**
     * Sets the physical lower left corner the corresponding image.
     * 
     * @param newlLowerLeftCorner
     *            Physical lower left corner the corresponding image
     */
    protected synchronized void setPhysicalLowerLeftCorner(Vector2d newlLowerLeftCorner) {
        lowerLeftCorner = newlLowerLeftCorner;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getDetector() {
        return detector;
    }

    /**
     * {@inheritDoc}
     */
    public String getInstrument() {
        return instrument;
    }

    /**
     * {@inheritDoc}
     */
    public String getMeasurement() {
        return measurement;
    }

    /**
     * {@inheritDoc}
     */
    public String getObservatory() {
        return observatory;
    }

    public String getFullName() {
        return fullName;
    }

    /**
     * {@inheritDoc}
     */
    public double getSunPixelRadius() {
        return solarPixelRadius;
    }

    /**
     * {@inheritDoc}
     */
    public Vector2d getSunPixelPosition() {
        return sunPixelPosition;
    }

    /**
     * {@inheritDoc}
     */
    public Vector2i getResolution() {
        return pixelImageSize;
    }

    /**
     * {@inheritDoc}
     */
    public double getUnitsPerPixel() {
        return meterPerPixel;
    }

    public LocalDateTime getLocalDateTime(){
    	return localDateTime;
    }
    
    public abstract boolean updatePixelParameters();

	public double getHEEX() {
        return heeX;
    }

    public double getHEEY() {
        return heeqY;
    }

    public double getHEEZ() {
        return heeZ;
    }

    public boolean isHEEProvided() {
        return heeAvailable;
    }

    public double getHEEQX() {
        return this.heeqX;
    }

    public double getHEEQY() {
        return this.heeqY;
    }

    public double getHEEQZ() {
        return this.heeqZ;
    }

    public boolean isHEEQProvided() {
        return this.heeqAvailable;
    }

    public double getCrln() {
        return crln;
    }

    public double getCrlt() {
        return crlt;
    }

    public double getDobs() {
        return dobs;
    }

    public boolean isCarringtonProvided() {
        return carringtonAvailable;
    }

    public boolean isStonyhurstProvided() {
        return stonyhurstAvailable;
    }

    public double getStonyhurstLatitude() {
        return stonyhurstLatitude;
    }

    public double getStonyhurstLongitude() {
        return stonyhurstLongitude;
    }
    
    /**
     * {@inheritDoc}
     */
    public double getInnerPhysicalOcculterRadius() {
        return innerRadius;
    }

    /**
     * {@inheritDoc}
     */
    public double getOuterPhysicalOcculterRadius() {
        return outerRadius;
    }

    /**
     * {@inheritDoc}
     */
    public double getPhysicalFlatOcculterSize() {
        return flatDistance;
    }

    public Vector2d getOcculterCenter() {
        return occulterCenter;
    }

	public double getMaskRotation() {
        return maskRotation;
	}

	public double getRadiusSuninArcsec() {
        double distanceToSun = metaDataContainer.tryGetDouble("DSUN_OBS");
        return Math.atan(Constants.SUN_RADIUS / distanceToSun) * MathUtils.RAD_TO_DEG * 3600;
	}

	protected void calcDefaultRotation() {
		defaultRotation = Quaternion3d.calcRotation(orientation,
				new Vector3d(0, 0, 1));
	}
	
	public Quaternion3d getRotation(){
		return this.defaultRotation;
	}
	
	public LUT_ENTRY getDefaultLUT(){
		return defaultLUT;
	}

	public void setDimension(int width, int height) {
		pixelImageSize = new Vector2i(width, height);
	}
	
}
