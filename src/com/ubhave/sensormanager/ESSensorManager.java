package com.ubhave.sensormanager;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.util.SparseArray;

import com.ubhave.sensormanager.config.GlobalConfig;
import com.ubhave.sensormanager.config.SensorConfig;
import com.ubhave.sensormanager.data.SensorData;
import com.ubhave.sensormanager.dutycyling.AdaptiveSensing;
import com.ubhave.sensormanager.logs.ESLogger;
import com.ubhave.sensormanager.sensors.SensorInterface;
import com.ubhave.sensormanager.sensors.SensorUtils;
import com.ubhave.sensormanager.sensors.pull.AbstractPullSensor;
import com.ubhave.sensormanager.tasks.AbstractSensorTask;
import com.ubhave.sensormanager.tasks.PullSensorTask;
import com.ubhave.sensormanager.tasks.PushSensorTask;
import com.ubhave.sensormanager.tasks.Subscription;
import com.ubhave.sensormanager.tasks.SubscriptionList;

public class ESSensorManager implements ESSensorManagerInterface, SensorDataListener
{
	private static final String TAG = "ESSensorManager";

	private static ESSensorManager sensorManager;
	private static Object lock = new Object();

	private final Context applicationContext;
	private PowerManager.WakeLock wakeLock;
	private final SparseArray<AbstractSensorTask> sensorTaskMap;
	private final SubscriptionList subscriptionList;
	private final GlobalConfig config;

	public static ESSensorManager getSensorManager(Context context) throws ESException
	{
		if (context == null)
		{
			throw new ESException(ESException.INVALID_PARAMETER, " Invalid parameter, context object passed is null");
		}
		if (sensorManager == null)
		{
			synchronized (lock)
			{
				if (sensorManager == null)
				{
					sensorManager = new ESSensorManager(context);
					sensorManager.setup();
					ESLogger.log(TAG, "started.");
				}
			}
		}
		return sensorManager;
	}

	private ESSensorManager(final Context appContext)
	{
		applicationContext = appContext;
		sensorTaskMap = new SparseArray<AbstractSensorTask>();
		subscriptionList = new SubscriptionList();
		config = GlobalConfig.getDefaultGlobalConfig();

		ArrayList<SensorInterface> sensors = SensorUtils.getAllSensors(appContext);

		for (SensorInterface aSensor : sensors)
		{
			AbstractSensorTask sensorTask;

			if (SensorUtils.isPullSensor(aSensor.getSensorType()))
			{
				sensorTask = new PullSensorTask(aSensor);
			}
			else
			{
				sensorTask = new PushSensorTask(aSensor);
			}

			sensorTask.start();

			sensorTaskMap.put(aSensor.getSensorType(), sensorTask);
		}
	}

	private void setup() throws ESException
	{
		// initial setup
		// register with battery sensor
		subscribeToSensorData(SensorUtils.SENSOR_TYPE_BATTERY, this);
	}

	public synchronized int subscribeToSensorData(int sensorId, SensorDataListener listener) throws ESException
	{
		AbstractSensorTask task = sensorTaskMap.get(sensorId);
		if (task != null)
		{
			ESLogger.log(TAG, "subscribeToSensorData() subscribing listener to sensorId " + sensorId);
			Subscription subscription = new Subscription(task, listener);
			int subscriptionId = subscriptionList.registerSubscription(subscription);
			return subscriptionId;
		}
		else
		{
			throw new ESException(ESException.UNKNOWN_SENSOR_TYPE, "Invalid sensor type: " + sensorId);
		}
	}

	public synchronized void unsubscribeFromSensorData(int subscriptionId) throws ESException
	{
		Subscription subscription = subscriptionList.removeSubscription(subscriptionId);
		if (subscription != null)
		{
			subscription.unregister();
		}
		else
		{
			throw new ESException(ESException.INVALID_STATE, "Un-Mapped subscription id: " + subscriptionId);
		}
	}

	private AbstractSensorTask getSensorTask(int sensorId) throws ESException
	{
		AbstractSensorTask sensorTask = sensorTaskMap.get(sensorId);
		if (sensorTask == null)
		{
			throw new ESException(ESException.UNKNOWN_SENSOR_TYPE, "Unknown sensor type: " + sensorId);
		}
		return sensorTask;
	}

	public SensorData getDataFromSensor(int sensorId) throws ESException
	{
		SensorData sensorData = null;
		AbstractSensorTask sensorTask = getSensorTask(sensorId);
		if (!SensorUtils.isPullSensor(sensorTask.getSensorType()))
		{
			throw new ESException(ESException.OPERATION_NOT_SUPPORTED, "this method is supported only for pull sensors.");
		}
		else if (sensorTask.isRunning())
		{
			throw new ESException(ESException.OPERATION_NOT_SUPPORTED, "this method is supported only for sensors that are not currently running. please unregister all listeners to the sensor and then call this method.");
		}
		else
		{
			sensorData = ((PullSensorTask) sensorTask).getCurrentSensorData();
		}

		return sensorData;
	}

	public void setSensorConfig(int sensorId, String configKey, Object configValue) throws ESException
	{
		AbstractSensorTask sensorTask = getSensorTask(sensorId);
		SensorInterface sensor = sensorTask.getSensor();
		sensor.setSensorConfig(configKey, configValue);

		if (configKey.equals(SensorConfig.ADAPTIVE_SENSING_ENABLED))
		{
			if ((Boolean) configValue)
			{
				enableAdaptiveSensing(sensorId);
			}
			else
			{
				disableAdaptiveSensing(sensorId);
			}
		}
	}

	public Object getSensorConfigValue(int sensorId, String configKey) throws ESException
	{
		AbstractSensorTask sensorTask = getSensorTask(sensorId);
		SensorInterface sensor = sensorTask.getSensor();
		return sensor.getSensorConfig(configKey);
	}

	@Override
	public void setGlobalConfig(String configKey, Object configValue) throws ESException
	{
		config.setParameter(configKey, configValue);

		if (configKey.equals(GlobalConfig.ACQUIRE_WAKE_LOCK))
		{
			if (applicationContext.checkCallingOrSelfPermission("android.permission.WAKE_LOCK") == PackageManager.PERMISSION_GRANTED)
			{
				throw new ESException(ESException.PERMISSION_DENIED, "Sensor Manager requires android.permission.WAKE_LOCK");
			}

			if ((Boolean) configValue)
			{
				acquireWakeLock();
			}
			else
			{
				releaseWakeLock();
			}
		}
	}

	@Override
	public Object getGlobalConfig(String configKey) throws ESException
	{
		return config.getParameter(configKey);
	}

	private void acquireWakeLock()
	{
		PowerManager pm = (PowerManager) applicationContext.getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wakelock");
		wakeLock.acquire();
	}

	private void releaseWakeLock()
	{
		if (wakeLock != null)
		{
			wakeLock.release();
		}
	}

	private void enableAdaptiveSensing(int sensorId) throws ESException
	{
		AbstractSensorTask sensorTask = getSensorTask(sensorId);
		if (SensorUtils.isPullSensor(sensorId))
		{
			AbstractPullSensor pullSensor = (AbstractPullSensor) sensorTask.getSensor();
			AdaptiveSensing.getAdaptiveSensing().registerSensor(sensorManager, sensorTask.getSensor(), SensorUtils.getSensorDataClassifier(sensorId), pullSensor);
		}
		else
		{
			throw new ESException(ESException.OPERATION_NOT_SUPPORTED, " adaptive sensing is supported only for pull sensors");
		}

	}

	private void disableAdaptiveSensing(int sensorId) throws ESException
	{
		AbstractSensorTask sensorTask = getSensorTask(sensorId);
		if (AdaptiveSensing.getAdaptiveSensing().isSensorRegistered(sensorTask.getSensor()))
		{
			AdaptiveSensing.getAdaptiveSensing().unregisterSensor(sensorManager, sensorTask.getSensor());
		}
		else
		{
			throw new ESException(ESException.OPERATION_NOT_SUPPORTED, " adaptive sensing not enabled for sensorId: " + sensorId);
		}
	}

	public void onDataSensed(SensorData data)
	{
		// ignore
	}

	public void onCrossingLowBatteryThreshold(boolean isBelowThreshold)
	{
		List<Subscription> subscribers = subscriptionList.getAllSubscriptions();
		for (Subscription sub : subscribers)
		{
			if (!sub.isPaused())
			{
				sub.getListener().onCrossingLowBatteryThreshold(isBelowThreshold);
			}
		}
	}

	public void pauseSubscription(int subscriptionId) throws ESException
	{
		Subscription s = subscriptionList.getSubscription(subscriptionId);
		s.pause();
	}

	public void unPauseSubscription(int subscriptionId) throws ESException
	{
		Subscription s = subscriptionList.getSubscription(subscriptionId);
		s.unpause();
	}

}
