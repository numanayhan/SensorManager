package com.ubhave.sensormanager;

import com.ubhave.sensormanager.data.SensorData;

public interface ESSensorManagerInterface
{
	/*
	 * Getting data from sensors
	 */
	public int subscribeToSensorData(int sensorId, SensorDataListener listener) throws ESException;

	public void unsubscribeFromSensorData(int subscriptionId) throws ESException;

	public SensorData getDataFromSensor(int sensorId) throws ESException;
	
	/*
	 * Pause / Unpause
	 */
	
	public void pauseSubscription(int subscriptionId) throws ESException;
	
	public void unPauseSubscription(int subscriptionId) throws ESException;
	
	/*
	 * Getting/setting configuration parameters
	 */
	public void setSensorConfig(int sensorId, String configKey, Object configValue) throws ESException;
	
	public Object getSensorConfigValue(int sensorId, String configKey) throws ESException;
	
	public void setGlobalConfig(String configKey, Object configValue) throws ESException;
	
	public Object getGlobalConfig(String configKey) throws ESException;
	
}
