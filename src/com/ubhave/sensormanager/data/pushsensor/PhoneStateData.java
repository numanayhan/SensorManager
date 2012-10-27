package com.ubhave.sensormanager.data.pushsensor;

import com.ubhave.sensormanager.data.SensorData;
import com.ubhave.sensormanager.sensors.SensorUtils;

public class PhoneStateData extends SensorData
{

	public static final int CALL_STATE_IDLE = 54401;
	public static final int CALL_STATE_OFFHOOK = 54402;
	public static final int CALL_STATE_RINGING = 54403;
	public static final int CALL_STATE_OUTGOING = 54404;
	public static final int ON_CELL_LOCATION_CHANGED = 5441;
	public static final int ON_DATA_ACTIVITY = 5442;
	public static final int ON_DATA_CONNECTION_STATE_CHANGED = 5443;
	public static final int ON_SERVICE_STATE_CHANGED = 5444;

	private int eventType;
	private String data;

	public PhoneStateData(long dataReceivedTimestamp, int eventType, String data)
	{
		super(dataReceivedTimestamp);
		this.eventType = eventType;
		this.data = data;
	}

	public int getEventType()
	{
		return eventType;
	}

	public String getData()
	{
		return data;
	}
	
	public boolean isRinging()
	{
		return eventType == CALL_STATE_RINGING;
	}
	
	public boolean isOffHook()
	{
		return eventType == CALL_STATE_OFFHOOK;
	}
	
	public boolean isIdle()
	{
		return eventType == CALL_STATE_IDLE;
	}

	public String getDataString()
	{
		return eventType + " " + data;
	}
	
	public int getSensorType()
	{
		return SensorUtils.SENSOR_TYPE_PHONE_STATE;
	}
}
