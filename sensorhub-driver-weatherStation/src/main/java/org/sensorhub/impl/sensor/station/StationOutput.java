/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Sensia Software LLC. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.station;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Text;
import net.opengis.swe.v20.Time;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataRecordImpl;
import org.vast.data.QuantityImpl;
import org.vast.data.TextEncodingImpl;
import org.vast.data.TextImpl;
import org.vast.data.TimeImpl;
import org.vast.sweCommon.SWEConstants;

/**
 * 
 * @author Tony Cook
 *
 *  ISSUES - we are pushing data here to the bus. How will that be controlled/timed
 */

public class StationOutput extends AbstractSensorOutput<StationSensor>
{
    private static final Logger log = LoggerFactory.getLogger(StationOutput.class);
    DataComponent stationDataStruct;
    DataBlock latestRecord;
    boolean sendData;
    Timer timer;
    double currentTrackPos;
    StationDataPoller dataPuller;

    public StationOutput(StationSensor parentSensor)
    {
        super(parentSensor);
        dataPuller = new StationDataPoller(); 
    }


    @Override
    public String getName()
    {
        return "GenericWeatherStation";
    }


    protected void init()
    {
        // SWE Common data structure
        stationDataStruct = new DataRecordImpl(3);
        stationDataStruct.setName(getName());
//        stationDataStruct.setDefinition("http://sensorml.com/ont/swe/property/Location");
        
        // stationName,time,lat,lon,el,Temperature (degreesF),Dewpoint (degreesF),Relative Humididty (%),Wind Speed (mph),Wind Direction (degrees),
        //Air Pressure (inches HG),Precipitation (inches),Heat Index (degreesF),Wind Chill (degreesF), Wind Gust (mph),
        //Rainfaill last 3 hours (inches),Rainfaill last 6 hours (inches),Rainfaill last 24 hours (inches),Max Temperature last 24 hours (degreesF),Min Temperature last 24 hours (degreesF),
        //cloud Ceiling (feet),visibility (feet)
        
        Text text = new TextImpl();
        stationDataStruct.addComponent("stationName", new TextImpl());
        
        Time c1 = new TimeImpl();
        c1.getUom().setHref(Time.ISO_TIME_UNIT);
        c1.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
        stationDataStruct.addComponent("time", c1);

        Quantity c;
        c = new QuantityImpl();
        c.getUom().setCode("deg");
        c.setDefinition("http://sensorml.com/ont/swe/property/Latitude");
        c.setReferenceFrame("http://www.opengis.net/def/crs/EPSG/0/4979");
        c.setAxisID("Lat");
        stationDataStruct.addComponent("lat",c);

        c = new QuantityImpl();
        c.getUom().setCode("deg");
        c.setDefinition("http://sensorml.com/ont/swe/property/Longitude");
        c.setReferenceFrame("http://www.opengis.net/def/crs/EPSG/0/4979");
        c.setAxisID("Long");
        stationDataStruct.addComponent("lon", c);

        c = new QuantityImpl();
        c.getUom().setCode("m");
        c.setDefinition("http://sensorml.com/ont/swe/property/Elevation");
        c.setReferenceFrame("http://www.opengis.net/def/crs/EPSG/0/4979");
        c.setAxisID("h");
        stationDataStruct.addComponent("alt", c);   
      
        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/Temperature");
        stationDataStruct.addComponent("temperature", c);  

        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/DewPoint"); //  does not resolve
        stationDataStruct.addComponent("dewpoint", c);  

        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/HumidityValue"); 
        stationDataStruct.addComponent("relativeHumidity", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("mi_i/h");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindSpeed"); 
        stationDataStruct.addComponent("windSpeed", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("deg");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindDirectionAngle"); 
        stationDataStruct.addComponent("windDirection", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("mi_i/h");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindSpeed"); 
        stationDataStruct.addComponent("windGust", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/Temperature"); 
        stationDataStruct.addComponent("minDailyTempearture", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/Temperature"); 
        stationDataStruct.addComponent("maxDailyTemperature", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("ft_i");
        c.setDefinition("http://sensorml.com/ont/swe/property/TopCloudHeightDimension.html"); 
        stationDataStruct.addComponent("cloudCeiling", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("ft_i");
        c.setDefinition("http://sensorml.com/ont/swe/property/Visibility");   // does not resolve
        stationDataStruct.addComponent("visibility", c);  
    }


    private void sendLatestRecord()
    {
    	StationDataRecord rec = dataPuller.pullStationData();
    	
//        // build and publish datablock
        DataBlock dataBlock = stationDataStruct.createDataBlock();
        Station stn = rec.getStation();
        dataBlock.setStringValue(0, stn.getName()); 
        dataBlock.setDoubleValue(1, stn.getLat()); 
        dataBlock.setDoubleValue(2, stn.getLon()); 
        dataBlock.setDoubleValue(3, stn.getElevation()); 
        dataBlock.setDoubleValue(4, rec.getTemperature()); 
        dataBlock.setDoubleValue(5, rec.getDewPoint()); 
        dataBlock.setDoubleValue(6, rec.getRelativeHumidity()); 
        dataBlock.setDoubleValue(7, rec.getWindSpeed()); 
        dataBlock.setDoubleValue(8, rec.getWindDirection()); 
        dataBlock.setDoubleValue(9, rec.getWindGust()); 
        dataBlock.setDoubleValue(10, rec.getMinDailyTemperature()); 
        dataBlock.setDoubleValue(11, rec.getMaxDailyTemperature()); 
        dataBlock.setIntValue(12, rec.getCloudCeiling()); 
        dataBlock.setIntValue(13, rec.getVisibility()); 
        
        eventHandler.publishEvent(new SensorDataEvent((double)rec.getTimeUtc(), StationOutput.this, dataBlock));
    }


    protected void start()
    {
        if (timer != null)
            return;
        timer = new Timer();
        
        // start main measurement generation thread
        TimerTask task = new TimerTask() {
            public void run()
            {
            	sendLatestRecord();
            }            
        };
        
        timer.scheduleAtFixedRate(task, 0, TimeUnit.SECONDS.toMillis(10));        
    }


    protected void stop()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return 1.0;
    }


    @Override 
    public DataComponent getRecordDescription()
    {
        return stationDataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return new TextEncodingImpl(",", "\n");
    }


    @Override
    public DataBlock getLatestRecord()
    {
        return latestRecord;
    }
    
    
    @Override
    public double getLatestRecordTime()
    {
        if (latestRecord != null)
            return latestRecord.getDoubleValue(0);
        
        return Double.NaN;
    }

}
