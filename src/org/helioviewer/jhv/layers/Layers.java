package org.helioviewer.jhv.layers;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.helioviewer.jhv.viewmodel.view.jp2view.newjpx.KakaduRender;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Layers {
	private static CopyOnWriteArrayList<LayerListener> layerListeners;
	private static CopyOnWriteArrayList<AbstractLayer> layers;
	private static int activeLayer = -1;
	private static int activeImageLayer = -1;

	private static KakaduRender renderer = new KakaduRender();
	private static boolean coronaVisibility = true;

	private static final Comparator<AbstractLayer> COMPARATOR = new Comparator<AbstractLayer>() {

		@Override
		public int compare(AbstractLayer o1, AbstractLayer o2) {
			if (!o1.isImageLayer && o2.isImageLayer) return 1;
			else if (o1.isImageLayer && !o2.isImageLayer) return -1;
			return 0;
		}
	};

	
	static {
		layers = new CopyOnWriteArrayList<AbstractLayer>();
		layerListeners = new CopyOnWriteArrayList<LayerListener>();
	}

	public static AbstractLayer addLayer(String uri) {
		ImageLayer layer = new ImageLayer(uri, renderer);
		layers.add(layer);
		layers.sort(COMPARATOR);
		updateOpacity(layer, false);
		if (layers.size() == 1 || activeImageLayer < 0) setActiveLayer(0);
		for (LayerListener renderListener : layerListeners) {
			renderListener.newlayerAdded();
		}
		if (layers.size() == 1) {
			layerChanged();
		}
		return layer;
	}
	
	private static void updateOpacity(AbstractImageLayer imageLayer, boolean remove){
		double counter = 0;
		for (AbstractLayer tmpLayer : layers){
			if (tmpLayer.isImageLayer()) counter++;
		}
		for (AbstractLayer tmpLayer : layers){
			if (tmpLayer.isImageLayer()){
				AbstractImageLayer tmpImageLayer = (AbstractImageLayer) tmpLayer;
				if (tmpImageLayer == imageLayer) tmpImageLayer.setOpacity(1/counter);
				else {
					if (remove)
						tmpImageLayer.setOpacity(tmpImageLayer.getOpacity() / ((counter-1) / counter));
					else
						tmpImageLayer.setOpacity(tmpImageLayer.getOpacity() * ((counter-1) / counter));
				}
			}
		}
	}

	public static void addLayer(AbstractLayer layer) {
		layers.add(layer);
		layers.sort(COMPARATOR);
		if (layers.size() == 1 || activeImageLayer < 0) setActiveLayer(0);
		if (layer.isImageLayer()) updateOpacity((AbstractImageLayer)layer, false);
		for (LayerListener renderListener : layerListeners) {
			renderListener.newlayerAdded();
		}
		if (layers.size() == 1) {
			layerChanged();
		}
	}

	public static ImageLayer addLayer(int id, LocalDateTime start,
			LocalDateTime end, int cadence, String name) {
		ImageLayer layer = new ImageLayer(id, renderer, start, end, cadence,
				name);
		layers.add(layer);
		layers.sort(COMPARATOR);
		boolean imageLayer = false;
		updateOpacity(layer, false);
		
		if (layers.size() == 1 || activeImageLayer < 0) setActiveLayer(0);
		for (LayerListener renderListener : layerListeners) {
			renderListener.newlayerAdded();
		}
		if (layers.size() == 1) {
			layerChanged();
		}
		return layer;
	}

	public static AbstractLayer getLayer(int idx) {
		if (idx >= 0 && idx < layers.size())
			return layers.get(idx);
		return null;
	}

	public static void removeLayer(int idx) {
		if (!layers.isEmpty()) {
			if (layers.get(idx).isImageLayer()){
				updateOpacity((AbstractImageLayer)layers.get(idx), true);				
			}
			layers.get(idx).remove();
			layers.remove(idx);
			if (layers.isEmpty())
				activeLayer = -1;
			int counter = 0;
			for (AbstractLayer layer : layers){
				if (layer.isImageLayer()){
					activeImageLayer = counter;					
					break;
				}
				counter++;
			}
			if (counter != activeImageLayer){
				activeImageLayer = -1;
				layerChanged();
			}
			for (LayerListener renderListener : layerListeners) {
				renderListener.newlayerRemoved(idx);
			}
		}
	}

	public static void addNewLayerListener(LayerListener renderListener) {
		layerListeners.add(renderListener);
	}

	public static int getLayerCount() {
		return layers.size();
	}

	private static void layerChanged() {
		if (activeLayer >= 0) {
			for (LayerListener renderListener : layerListeners) {
				renderListener.activeLayerChanged(getLayer(activeLayer));
			}
		}
	}

	public static int getActiveLayerNumber() {
		return activeLayer;
	}

	public static AbstractLayer getActiveLayer() {
		if (layers.size() > 0 && activeLayer >= 0)
			return layers.get(activeLayer);
		return null;
	}

	public static void setActiveLayer(int activeLayer) {
		if ((Layers.activeLayer != activeLayer || Layers.activeImageLayer < 0) && getLayerCount() > 0) {
			Layers.activeLayer = activeLayer;
			if (getActiveLayer() != null && getActiveLayer().isImageLayer())
				Layers.activeImageLayer = activeLayer;
			Layers.layerChanged();
		}
	}

	public static CopyOnWriteArrayList<AbstractLayer> getLayers() {
		return layers;
	}

	public static void toggleCoronaVisibility() {
		coronaVisibility = !coronaVisibility;
	}

	public static boolean getCoronaVisibility() {
		return coronaVisibility;
	}

	public static void removeAllImageLayers() {
		for (AbstractLayer layer : layers){
			if (layer.isImageLayer){
				layer.remove();
				layers.remove(layer);
			}
		}
		activeLayer = 0;
		for (LayerListener renderListener : layerListeners) {
			renderListener.newlayerRemoved(0);
		}
	}

	public static void writeStatefile(JSONArray jsonLayers) {
		for (AbstractLayer layer : layers) {
			JSONObject jsonLayer = new JSONObject();
			layer.writeStateFile(jsonLayer);
			jsonLayers.put(jsonLayer);
		}
	}

	public static void readStatefile(JSONArray jsonLayers) {
		for (int i = 0; i < jsonLayers.length(); i++) {
			try {
				JSONObject jsonLayer = jsonLayers.getJSONObject(i);
				AbstractImageLayer layer = ImageLayer.readStateFile(jsonLayer,
						renderer);
				if (layer != null) {
					Layers.addLayer(layer);
					layer.readStateFile(jsonLayer);
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static AbstractImageLayer getActiveImageLayer() {
		if (activeImageLayer >= 0)
			return (AbstractImageLayer) layers.get(activeImageLayer);
		return null;
	}
}
