package id.com.yoyox.configuration;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;


/**
 * Created by Yoyok_T on 08/09/2018.
 */
public class ConfigurationManager {
    private static Logger log = Logger.getLogger(ConfigurationManager.class);
    private static Map<Class, ConfigData> configDataMap = new ConcurrentHashMap();

    private ConfigurationManager() {
    }

    public static <T extends ConfigData> void loadConfigData(Class<T>[] configDataClasses) {
        log.info("Loading the Configuration Settings...");
        Class[] arr$ = configDataClasses;
        int len$ = configDataClasses.length;

        for(int i$ = 0; i$ < len$; ++i$) {
            Class configDataClass = arr$[i$];
            configDataMap.put(configDataClass, createConfigData(configDataClass));
        }

    }

    public static void reloadConfigurations() {
        log.info("Reloading the Configuration Settings...");
        loadConfigData((Class[])configDataMap.keySet().toArray(new Class[configDataMap.keySet().size()]));
    }

    public static <T extends ConfigData> T get(Class<T> configDataClass) {
        if(!configDataMap.containsKey(configDataClass)) {
            configDataMap.put(configDataClass, createConfigData(configDataClass));
        }

        return (ConfigData)configDataMap.get(configDataClass);
    }

    public static DataSourceConfiguration getDataSourceConfiguration(String connectionName) {
        DataSourceConfiguration dataSourceConfiguration = (DataSourceConfiguration)((ConnectionConfigData)get(ConnectionConfigData.class)).getConnections().get(connectionName);
        if(dataSourceConfiguration == null) {
            dataSourceConfiguration = new DataSourceConfiguration();
        }

        return dataSourceConfiguration;
    }

    private static <T extends ConfigData> T createConfigData(Class<T> configDataClass) {
        try {
            log.info(String.format("%s config settings loading", new Object[]{configDataClass.getName()}));
            ConfigData e = (ConfigData)configDataClass.newInstance();
            log.info(String.format("%s config settings loaded", new Object[]{configDataClass.getName()}));
            return e;
        } catch (InstantiationException var2) {
            log.error(var2);
            throw new RuntimeException("Failed to load configuration data.", var2);
        } catch (IllegalAccessException var3) {
            log.error(var3);
            throw new RuntimeException("Failed to load configuration data.", var3);
        } catch (ExceptionInInitializerError var4) {
            log.error(var4);
            throw new RuntimeException(String.format("Failed to load configuration data for \'%s\'.", new Object[]{configDataClass.getName()}), var4);
        }
    }

    public static Object getCurrentSettingValue(String className, String propertyName) {
        return findCurrentSettingValue(className, propertyName);
    }

    public static boolean updateCurrentSettingValue(String className, String propertyName, String newValue) {
        return doUpdateCurrentSettingValue(className, propertyName, newValue);
    }

    private static boolean isPropertiesCollection(String propertyName) {
        return propertyName.contains("[") && propertyName.endsWith("]");
    }

    private static boolean isCollectionProperty(String propertyName) {
        return propertyName.contains("[") && !propertyName.endsWith("]");
    }

    private static String[] prepareVariables(String propertyName) {
        String realPropertyName = propertyName;
        String collectionKey = null;
        String valuePropertyName = null;
        if(isCollectionProperty(propertyName) || isPropertiesCollection(propertyName)) {
            realPropertyName = propertyName.substring(0, propertyName.indexOf("["));
            collectionKey = propertyName.substring(propertyName.indexOf("[") + 1, propertyName.indexOf("]"));
            if(isCollectionProperty(propertyName)) {
                valuePropertyName = propertyName.substring(propertyName.indexOf("]") + 2);
            } else if(isPropertiesCollection(propertyName)) {
                valuePropertyName = propertyName.substring(0, propertyName.indexOf("["));
            }
        }

        return new String[]{realPropertyName, collectionKey, valuePropertyName};
    }

    private static Object findCurrentSettingValue(String className, String propertyName) {
        try {
            String[] e = prepareVariables(propertyName);
            String realPropertyName = e[0];
            String collectionKey = e[1];
            String valuePropertyName = e[2];
            Iterator i$ = configDataMap.values().iterator();

            while(i$.hasNext()) {
                ConfigData configData = (ConfigData)i$.next();
                if(configData != null && configData.getClass().getName().equalsIgnoreCase(className)) {
                    Field field = configData.getClass().getDeclaredField(realPropertyName);
                    if(field != null) {
                        if(isCollectionProperty(propertyName)) {
                            Class[] var21 = field.getType().getClasses();
                            Class[] var22 = var21;
                            int len$ = var21.length;

                            for(int i$1 = 0; i$1 < len$; ++i$1) {
                                Class var10000 = var22[i$1];
                                if(field.getType().getName().equals(Map.class.getName())) {
                                    field.setAccessible(true);
                                    Map map = (Map)field.get(configData);
                                    if(map.containsKey(collectionKey)) {
                                        Object mapValue = map.get(collectionKey);
                                        Field valueField = mapValue.getClass().getDeclaredField(valuePropertyName);
                                        if(valueField != null) {
                                            valueField.setAccessible(true);
                                            return valueField.get(mapValue);
                                        }

                                        return null;
                                    }

                                    return null;
                                }
                            }

                            log.debug("Tried to access a property that was not a supported Collection Type!!");
                            return null;
                        }

                        if(isPropertiesCollection(propertyName)) {
                            field.setAccessible(true);
                            Properties props = null;
                            Object obj = field.get(configData);
                            if(obj instanceof Properties) {
                                props = (Properties)field.get(configData);
                            } else if(obj instanceof Map) {
                                return ((Map)obj).get(collectionKey);
                            }

                            if(props != null && props.containsKey(collectionKey)) {
                                return props.get(collectionKey);
                            }

                            return null;
                        }

                        field.setAccessible(true);
                        return field.get(configData);
                    }

                    return null;
                }
            }
        } catch (SecurityException var17) {
            log.error(var17 + String.format(" - Property: %s", new Object[]{className + "." + propertyName}));
        } catch (NoSuchFieldException var18) {
            log.error(var18 + String.format(" - Property: %s", new Object[]{className + "." + propertyName}));
        } catch (IllegalArgumentException var19) {
            log.error(var19 + String.format(" - Property: %s", new Object[]{className + "." + propertyName}));
        } catch (IllegalAccessException var20) {
            log.error(var20 + String.format(" - Property: %s", new Object[]{className + "." + propertyName}));
        }

        return null;
    }

    private static boolean doUpdateCurrentSettingValue(String className, String propertyName, Object newValue) {
        try {
            String[] e = prepareVariables(propertyName);
            String realPropertyName = e[0];
            String collectionKey = e[1];
            String valuePropertyName = e[2];
            Iterator i$ = configDataMap.values().iterator();

            while(i$.hasNext()) {
                ConfigData configData = (ConfigData)i$.next();
                if(configData != null && configData.getClass().getName().equalsIgnoreCase(className)) {
                    Field field = configData.getClass().getDeclaredField(realPropertyName);
                    if(field != null) {
                        if(isCollectionProperty(propertyName)) {
                            Class[] var22 = field.getType().getClasses();
                            Class[] arr$ = var22;
                            int len$ = var22.length;

                            for(int i$1 = 0; i$1 < len$; ++i$1) {
                                Class var10000 = arr$[i$1];
                                if(field.getType().getName().equals(Map.class.getName())) {
                                    field.setAccessible(true);
                                    Map map = (Map)field.get(configData);
                                    if(map.containsKey(collectionKey)) {
                                        Object mapValue = map.get(collectionKey);
                                        Field valueField = mapValue.getClass().getDeclaredField(valuePropertyName);
                                        if(valueField != null) {
                                            valueField.setAccessible(true);
                                            valueField.set(mapValue, newValue);
                                            return true;
                                        }

                                        return false;
                                    }

                                    return false;
                                }
                            }

                            log.debug("Tried to access a property that was not a supported Collection Type!!");
                            return false;
                        }

                        if(isPropertiesCollection(propertyName)) {
                            field.setAccessible(true);
                            Properties props = (Properties)field.get(configData);
                            if(props.containsKey(collectionKey)) {
                                props.setProperty(collectionKey, (String)newValue);
                                return true;
                            }

                            return false;
                        }

                        field.setAccessible(true);
                        if(field.getType() == String.class) {
                            field.set(configData, newValue);
                        } else if(field.getType() == Integer.TYPE) {
                            field.set(configData, Integer.valueOf((String)newValue));
                        } else if(field.getType() == Long.TYPE) {
                            field.set(configData, Long.valueOf((String)newValue));
                        } else if(field.getType() == Float.TYPE) {
                            field.set(configData, Float.valueOf((String)newValue));
                        } else if(field.getType() == Double.TYPE) {
                            field.set(configData, Double.valueOf((String)newValue));
                        } else {
                            if(field.getType() != Boolean.TYPE) {
                                log.error("Cannot update field value - it\'s not a Supported field type: " + field.getType().toString());
                                return false;
                            }

                            field.set(configData, Boolean.valueOf(newValue.toString().equalsIgnoreCase("true")));
                        }

                        return true;
                    }

                    return false;
                }
            }
        } catch (SecurityException var18) {
            log.error(var18);
        } catch (NoSuchFieldException var19) {
            log.error(var19);
        } catch (IllegalArgumentException var20) {
            log.error(var20);
        } catch (IllegalAccessException var21) {
            log.error(var21);
        }

        return false;
    }
}
