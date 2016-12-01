package org.sagebionetworks.bridge.models;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;

/**
 * <p>
 * Parsed representation of the User-Agent header provided by the client, when it is in one of our prescribed formats:
 * </p>
 * 
 * <p>
 * appName/appVersion<br>
 * appName/appVersion sdkName/sdkVersion<br>
 * appName/appVersion (deviceName; osName/osVersion) sdkName/sdkVersion
 * </p>
 * 
 * <p>
 * The full User-Agent header must be provided to enable the filtering of content based on the version of the application 
 * making a request (because versioning information is specific to the name of the OS on which the app is running; we 
 * currently expect either "iPhone OS", "iOS", or "Android", but any value can be used and will work as long as it is 
 * also set in the filtering criteria).
 * </p>
 * 
 * <p>Some examples:</p>
 * 
 * <ul>
 * <li>Melanoma Challenge Application/1</li>
 * <li>Unknown Client/14 BridgeJavaSDK/10</li>
 * <li>Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4</li>
 * <li>CardioHealth/1 (iPhone 6.0; iPhone OS/9.0.2) BridgeSDK/10</li>
 * </ul>
 * 
 * <p>
 * Other clients with more typical browser user agent strings will be represented by ClientInfo.UNKNOWN_CLIENT. This is
 * a "null" object with all empty fields. Some examples of these headers, from our logs:
 * </p>
 * 
 * <ul>
 * <li>Amazon Route 53 Health Check Service; ref:c97cd53f-2272-49d6-a8cd-3cd658d9d020; report http://amzn.to/1vsZADi</li>
 * <li>Integration Tests (Linux/3.13.0-36-generic) BridgeJavaSDK/3</li>
 * </ul>
 * 
 * <p>
 * ClientInfo is not the end result of a generic user agent string parser. Those are very complicated and we do not need
 * all this information (we always log the user agent string as we receive it from the client, but only use these
 * strings in our system when they are in format specified above).
 * </p>
 *
 */
public final class ClientInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientInfo.class);

    /**
     * Apple has changed the name of the iOS platform from "iPhone OS" to "iOS" and this is reflected in the 
     * User-Agent string we send. To avoid confusion, recognize such synonyms/spelling errors and map them
     * to our two canonical platforms, "iPhone OS" and "Android". 
     */
    private static final Map<String,String> PLATFORM_SYNONYMS = new ImmutableMap.Builder<String,String>()
            .put("iOS", "iPhone OS").build();
    
    /**
     * A cache of ClientInfo objects that have already been parsed from user agent strings. 
     * We're using this, rather than ConcurrentHashMap, because external clients submit this string, 
     * and thus could create an infinite number of them, starving the server. The cache will protect 
     * against this with its size limit.
     */
    private static final LoadingCache<String, ClientInfo> userAgents = CacheBuilder.newBuilder()
       .maximumSize(500)
       .build(new CacheLoader<String,ClientInfo>() {
            @Override
            public ClientInfo load(String userAgent) throws Exception {
                return ClientInfo.parseUserAgentString(userAgent);
            }
       });

    /**
     * A User-Agent string that does not follow our format is simply an unknown client, and no filtering will be done
     * for such a client. It is represented with a null object that is the ClientInfo object with all null fields. The
     * User-Agent header is still logged exactly as it is retrieved from the request.
     */
    public static final ClientInfo UNKNOWN_CLIENT = new ClientInfo.Builder().build();

    /**
     * For example, "App Name/14".
     */
    private static final Pattern SHORT_STRING = Pattern.compile("^([^/]+)\\/(\\d{1,9})($)");
    /**
     * For example, "Unknown Client/14 BridgeJavaSDK/10".
     */
    private static final Pattern MEDIUM_STRING = Pattern.compile("^([^/]+)\\/(\\d{1,9})\\s([^/\\(]*)\\/(\\d{1,9})($)");
    /**
     * For example, "Asthma/26 (Unknown iPhone; iPhone OS 9.1) BridgeSDK/4" or 
     * "Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4"
     */
    private static final Pattern LONG_STRING = Pattern
            .compile("^([^/]+)\\/(\\d{1,9})\\s\\(([^;]+);([^\\)]*)\\)\\s([^/]*)\\/(\\d{1,9})($)");
     
    private final String appName;
    private final Integer appVersion;
    private final String deviceName;
    private final String osName;
    private final String osVersion;
    private final String sdkName;
    private final Integer sdkVersion;

    @JsonCreator
    private ClientInfo(@JsonProperty("appName") String appName, @JsonProperty("appVersion") Integer appVersion,
            @JsonProperty("deviceName") String deviceName, @JsonProperty("osName") String osName,
            @JsonProperty("osVersion") String osVersion, @JsonProperty("sdkName") String sdkName,
            @JsonProperty("sdkVersion") Integer sdkVersion) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.deviceName = deviceName;
        this.osName = osName;
        this.osVersion = osVersion;
        this.sdkName = sdkName;
        this.sdkVersion = sdkVersion;
    }

    public String getAppName() {
        return appName;
    }

    public Integer getAppVersion() {
        return appVersion;
    }
    
    public String getDeviceName() {
        return deviceName;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getSdkName() {
        return sdkName;
    }

    public Integer getSdkVersion() {
        return sdkVersion;
    }
    
    public boolean isSupportedAppVersion(Integer minSupportedVersion) {
    	// If both the appVersion and minSupportedVersion are defined, check that the appVersion is 
    	// greater than or equal to the minSupportedVersion
    	return (appVersion == null || minSupportedVersion == null || appVersion >= minSupportedVersion);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(appName);
        result = prime * result + Objects.hashCode(appVersion);
        result = prime * result + Objects.hashCode(deviceName);
        result = prime * result + Objects.hashCode(osName);
        result = prime * result + Objects.hashCode(osVersion);
        result = prime * result + Objects.hashCode(sdkName);
        result = prime * result + Objects.hashCode(sdkVersion);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ClientInfo other = (ClientInfo) obj;
        return Objects.equals(appName, other.appName) && Objects.equals(appVersion, other.appVersion)
                && Objects.equals(osName, other.osName) && Objects.equals(osVersion, other.osVersion)
                && Objects.equals(sdkName, other.sdkName) && Objects.equals(sdkVersion, other.sdkVersion) 
                && Objects.equals(deviceName, other.deviceName);
    }

    @Override
    public String toString() {
        return "ClientInfo [appName=" + appName + ", appVersion=" + appVersion + ", deviceName=" + deviceName + ", osName=" + osName + ", osVersion="
                + osVersion + ", sdkName=" + sdkName + ", sdkVersion=" + sdkVersion + "]";
    }

    static class Builder {
        private String appName;
        private Integer appVersion;
        private String deviceName;
        private String osName;
        private String osVersion;
        private String sdkName;
        private Integer sdkVersion;

        public Builder withAppName(String appName) {
            this.appName = appName;
            return this;
        }
        public Builder withAppVersion(Integer appVersion) {
            this.appVersion = appVersion;
            return this;
        }
        public Builder withDeviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }
        public Builder withOsName(String osName) {
            this.osName = osName;
            return this;
        }
        public Builder withOsVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }
        public Builder withSdkName(String sdkName) {
            this.sdkName = sdkName;
            return this;
        }
        public Builder withSdkVersion(Integer sdkVersion) {
            this.sdkVersion = sdkVersion;
            return this;
        }
        /**
         * It's valid to have a client info object with no fields, if the
         * User-Agent header is not in our prescribed format.
         */
        public ClientInfo build() {
            if (PLATFORM_SYNONYMS.containsKey(osName)) {
                osName = PLATFORM_SYNONYMS.get(osName);
            }
            return new ClientInfo(appName, appVersion, deviceName, osName, osVersion, sdkName, sdkVersion);
        }

    }
    
    /**
     * Get a ClientInfo object given a User-Agent header string. These values are cached and 
     * headers that are not in the prescribed format return an empty client info object.
     * @param userAgent
     * @return
     */
    public static ClientInfo fromUserAgentCache(String userAgent) {
        if (!StringUtils.isBlank(userAgent)) {
            try {
                return userAgents.get(userAgent);    
            } catch(ExecutionException e) {
                // This should not happen, the CacheLoader doesn't throw exceptions
                // Log it and return UNKNOWN_CLIENT
                LOGGER.error(e.getMessage(), e);
            }
        }
        return UNKNOWN_CLIENT;
    }
    
    static ClientInfo parseUserAgentString(String ua) {
        ClientInfo info = UNKNOWN_CLIENT;
        if (!StringUtils.isBlank(ua)) {
            info = parseLongUserAgent(ua);
            if (info == UNKNOWN_CLIENT) {
                info = parseMediumUserAgent(ua);
            }
            if (info == UNKNOWN_CLIENT) {
                info = parseShortUserAgent(ua);
            }
        }
        return info;
    }
    
    private static ClientInfo parseLongUserAgent(String ua) {
        Matcher matcher = LONG_STRING.matcher(ua);
        if (matcher.matches()) {
            // Pull out the information that matches both deprecated and new format
            Builder builder = new ClientInfo.Builder()
                .withAppName(matcher.group(1).trim())
                .withAppVersion(Integer.parseInt(matcher.group(2).trim()))
                .withDeviceName(matcher.group(3).trim())
                .withSdkName(matcher.group(5).trim())
                .withSdkVersion(Integer.parseInt(matcher.group(6).trim()));
        	
            // Older vesions of iOS apps that use the BridgeSDK have a space between 
            // the osName and osVersion, whereas newer format is to use a forward slash
            // to separate the two parts. syoung 11/19/2015
            String osInfo = matcher.group(4).trim();
            String[] osParts = osInfo.split("/");
            if (osParts.length == 2) {
                builder = builder
                    .withOsName(osParts[0].trim())
                    .withOsVersion(osParts[1].trim());
            }
            else {
                int idx = osInfo.lastIndexOf(" ");
        	    if (idx > 0) {
        	        builder = builder
        	            .withOsName(osInfo.substring(0,idx).trim())
        	            .withOsVersion(osInfo.substring(idx).trim());
                }
            }
        	
            return builder.build();
        }
        return UNKNOWN_CLIENT;
    }

    private static ClientInfo parseMediumUserAgent(String ua) {
        Matcher matcher = MEDIUM_STRING.matcher(ua);
        if (matcher.matches()) {
            return new ClientInfo.Builder()
                .withAppName(matcher.group(1).trim())
                .withAppVersion(Integer.parseInt(matcher.group(2).trim()))
                .withSdkName(matcher.group(3).trim())
                .withSdkVersion(Integer.parseInt(matcher.group(4).trim())).build();
        }
        return UNKNOWN_CLIENT;
    }

    private static ClientInfo parseShortUserAgent(String ua) {
        Matcher matcher = SHORT_STRING.matcher(ua);
        if (matcher.matches()) {
            return new ClientInfo.Builder()
                .withAppName(matcher.group(1).trim())
                .withAppVersion(Integer.parseInt(matcher.group(2).trim())).build();
        }
        return UNKNOWN_CLIENT;
    }
}
