package org.helioviewer.jhv.base.math;

/**
 * A collection of useful static methods.
 */
public class MathUtils
{
    public static final double RAD_TO_DEG = 180.0 / Math.PI;

    public static int clip(int _val, int _lower, int _upper)
    {
        if (_val <= _lower)
            return _lower;
        else if (_val >= _upper)
            return _upper;
        else
            return _val;
    }
    
    public static double clip(double _val, double _lower, double _upper)
    {
        if (_val <= _lower)
            return _lower;
        else if (_val >= _upper)
            return _upper;
        else
            return _val;
    }
    
    /**
     * Converts a linear ramp to a cosine ramp.
     * 
     * @param _val A value from 0..1
     * @return A value from 0..1
     */
    public static double cosinize(double _val)
    {
    	return 0.5-0.5*Math.cos(_val*Math.PI);
    }

    /**
     * Takes and returns the maximum value from the given args.
     * 
     * @param _is
     *            the values to compare
     * @return the maximum of the given values
     */
    public static int max(int... _is)
    {
        int max = Integer.MIN_VALUE;
        for (int i : _is)
            if (max < i)
                max = i;
        return max;
    }

    /**
     * Takes and returns the minimum value from the given args.
     * 
     * @param _is
     *            the values to compare
     * @return the minimum of the given values
     */
    public static int min(int... _is) {
        int min = Integer.MAX_VALUE;
        for (int i : _is)
            if (min > i)
                min = i;
        return min;
    }

    public static double mapTo0To360(double x) {
        double tmp = x % 360.0;
        if (tmp < 0) {
            return tmp + 360.0;
        } else {
            return tmp;
        }
    }
    
    public static int nextPowerOfTwo(int value)
    {
    	value--;
    	value |= value >> 1;
    	value |= value >> 2;
    	value |= value >> 4;
    	value |= value >> 8;
    	value |= value >> 16;
    	value++;
    	return value;
    }
}
