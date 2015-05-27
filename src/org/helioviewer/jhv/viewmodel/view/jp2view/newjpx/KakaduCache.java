package org.helioviewer.jhv.viewmodel.view.jp2view.newjpx;

import kdu_jni.Kdu_cache;

public class KakaduCache implements JHVCachable {
			
	private int size;
	
	private Kdu_cache cache;
	
	private int insturmentID;
	
	private boolean complete;	
	
	public KakaduCache(int insturmentID){
		size = 0;
		this.insturmentID = insturmentID;
		this.cache = new Kdu_cache();
	}
		
	public Kdu_cache getCache(){
		return cache;
	}
	
	public void addSize(int size){
		this.size += size;
	}
	
	public int getSize(){
		return this.size;
	}
}