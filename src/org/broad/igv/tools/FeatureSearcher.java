/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.tools;

import org.apache.log4j.Logger;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.FeatureSource;
import org.broad.igv.track.FeatureTrack;
import org.broad.tribble.Feature;

import java.io.IOException;
import java.util.Iterator;

/**
 * Used for searching for the next feature, given a source.
 * We simply call getFeature on that source repeatedly over stepped windows
 * until we find something
 * User: jacob
 * Date: 2013-Feb-21
 */
public class FeatureSearcher implements Runnable {

    private static Logger log = Logger.getLogger(FeatureSearcher.class);

    private FeatureTrack track = null;
    private FeatureSource<? extends Feature> source = null;

    private static final int DEFAULT_SEARCH_WINDOW_SIZE = 100000;

    /**
     * The window size over which to search, in base pairs
     */
    private int searchWindowSize = DEFAULT_SEARCH_WINDOW_SIZE;

    private volatile Iterator<? extends Feature> result = null;

    private volatile boolean isRunning = false;
    private volatile boolean wasCancelled = false;

    private final Genome genome;
    private String chr;
    private int start;
    private int end;

//    public FeatureSearcher(FeatureTrack track, String chr, int start, int end){
//        assert track != null;
//        this.track = track;
//    }

    /**
     *
     * @param source FeatureSource which we are searching
     * @param genome
     * @param chr
     * @param start
     */
    public FeatureSearcher(FeatureSource<? extends Feature> source, Genome genome, String chr, int start){
        assert source != null;
        this.source = source;
        this.genome = genome;
        this.initSearchCoords(chr, start);
    }

    private void initSearchCoords(String chr, int start){
        this.chr = chr;
        this.start = start;
        this.end = start + searchWindowSize;
    }

    private void incrementSearchCoords(){
        this.start += searchWindowSize;
        int maxCoord = Integer.MAX_VALUE - searchWindowSize;
        if(this.genome != null){
            maxCoord = genome.getChromosome(chr).getLength();
        }

        if (start >= maxCoord) {
            //System.out.println("start greater than maxcoord " + maxCoord + " in chromosome " + chr);
            if(genome != null){
                chr = genome.getNextChrName(chr);
            }else{
                chr = null;
            }

            if (chr == null) {
                //No next chromosome, done searching
                start = end = -1;
                this.cancel();
                return;
            } else {
                start = 0;
                maxCoord = genome.getChromosome(chr).getLength();
            }
        }
        this.end = start + searchWindowSize;
        this.end = Math.min(this.end, maxCoord);
    }

    private Iterator<? extends Feature> getFeatures(String chr, int start, int end) throws IOException{
        if(track != null) return track.getFeatures(chr, start, end).iterator();
        if(source != null) return source.getFeatures(chr, start, end);
        throw new IllegalStateException("Have no FeatureTrack or FeatureSource from which to get features");
    }

    /**
     * Signal the searcher to stop. Note that stopping may not be instantaneous
     */
    public void cancel(){
        this.wasCancelled = true;
    }

    public boolean isRunning(){
        return this.isRunning;
    }

    public Iterator<? extends Feature> getResult(){
        if(this.isRunning) return null;
        return this.result;
    }

    public void setSearchWindowSize(int searchWindowSize) {
        this.searchWindowSize = searchWindowSize;
        this.end = this.start + this.searchWindowSize;
    }

    @Override
    public void run() {
        isRunning = true;
        Iterator<? extends Feature> rslt = null;

        while(isRunning && !wasCancelled){
            try {
                rslt = getFeatures(chr, start, end);
                if(rslt != null && rslt.hasNext()){
                    //Found something
                    this.result = rslt;
                    break;
                }else{
                    //Didn't find anything, keep going
                    incrementSearchCoords();
                }
            } catch (IOException e) {
                log.error("Error searching for feature", e);
                break;
            }
        }
        this.isRunning = false;
    }
}
