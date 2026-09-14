package com.sipgate.sparta.hss.ims;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "IMSSubscription")
public class ImsSubscription implements Serializable {

    @Serial
    private static final long serialVersionUID = 20230503163500L;

    private String privateId;
    private List<ServiceProfile> serviceProfiles;
    private ImsSubscriptionExtension extension;

    @XmlElement(name = "PrivateID")
    public String getPrivateId() {
        return privateId;
    }

    public ImsSubscription setPrivateId(final String privateId) {
        this.privateId = privateId;
        return this;
    }

    @XmlElement(name = "ServiceProfile")
    public List<ServiceProfile> getServiceProfiles() {
        return serviceProfiles;
    }

    public ImsSubscription setServiceProfiles(
            final List<ServiceProfile> serviceProfiles) {
        this.serviceProfiles = serviceProfiles;
        return this;
    }

    @XmlElement(name = "Extension")
    public ImsSubscriptionExtension getExtension() {
        return extension;
    }

    public ImsSubscription setExtension(
            final ImsSubscriptionExtension extension) {
        this.extension = extension;
        return this;
    }

    public static class ServiceProfile implements Serializable {

        @Serial
        private static final long serialVersionUID = 20230503163500L;

        private List<PublicIdentity> publicIdentity;
        private List<InitialFilterCriteria> initialFilterCriteria;
        private ServiceProfileExtension extension;
        private CoreNetworkServicesAuthorization coreNetworkServicesAuthorization;

        @XmlElement(name = "PublicIdentity")
        public List<PublicIdentity> getPublicIdentity() {
            return publicIdentity;
        }

        public ServiceProfile setPublicIdentity(
                final List<PublicIdentity> publicIdentity) {
            this.publicIdentity = publicIdentity;
            return this;
        }

        @XmlElement(name = "InitialFilterCriteria")
        public List<InitialFilterCriteria> getInitialFilterCriteria() {
            return initialFilterCriteria;
        }

        public ServiceProfile setInitialFilterCriteria(
                final List<InitialFilterCriteria> initialFilterCriteria) {
            this.initialFilterCriteria = initialFilterCriteria;
            return this;
        }

        @XmlElement(name = "Extension")
        public ServiceProfileExtension getExtension() {
            return extension;
        }

        public ServiceProfile setExtension(
                final ServiceProfileExtension extension) {
            this.extension = extension;
            return this;
        }

        @XmlElement(name = "CoreNetworkServicesAuthorization")
        public CoreNetworkServicesAuthorization getCoreNetworkServicesAuthorization() {
            return coreNetworkServicesAuthorization;
        }

        public ServiceProfile setCoreNetworkServicesAuthorization(
                final CoreNetworkServicesAuthorization coreNetworkServicesAuthorization) {
            this.coreNetworkServicesAuthorization = coreNetworkServicesAuthorization;
            return this;
        }

        public static class PublicIdentity implements Serializable {

            @Serial
            private static final long serialVersionUID = 20230503163500L;

            private String barringIndication;
            private String identity;
            private PublicIdentityExtension extension;

            @XmlElement(name = "BarringIndication")
            public String getBarringIndication() {
                return barringIndication;
            }

            public PublicIdentity setBarringIndication(final String barringIndication) {
                this.barringIndication = barringIndication;
                return this;
            }

            @XmlElement(name = "Identity")
            public String getIdentity() {
                return identity;
            }

            public PublicIdentity setIdentity(final String identity) {
                this.identity = identity;
                return this;
            }

            @XmlElement(name = "Extension")
            public PublicIdentityExtension getExtension() {
                return extension;
            }

            public PublicIdentity setExtension(
                    final PublicIdentityExtension extension) {
                this.extension = extension;
                return this;
            }

            public static class PublicIdentityExtension implements Serializable {

                @Serial
                private static final long serialVersionUID = 20230503163500L;

                private Integer identityType;
                private String wildcardedPsi;
                private PublicIdentityExtension2 extension;

                @XmlElement(name = "IdentityType")
                public Integer getIdentityType() {
                    return identityType;
                }

                public PublicIdentityExtension setIdentityType(final Integer identityType) {
                    this.identityType = identityType;
                    return this;
                }

                @XmlElement(name = "WildcardedPSI")
                public String getWildcardedPsi() {
                    return wildcardedPsi;
                }

                public PublicIdentityExtension setWildcardedPsi(final String wildcardedPsi) {
                    this.wildcardedPsi = wildcardedPsi;
                    return this;
                }

                @XmlElement(name = "Extension")
                public PublicIdentityExtension2 getExtension() {
                    return extension;
                }

                public PublicIdentityExtension setExtension(
                        final PublicIdentityExtension2 extension) {
                    this.extension = extension;
                    return this;
                }

                public static class PublicIdentityExtension2 implements Serializable {

                    @Serial
                    private static final long serialVersionUID = 20230503163500L;

                    private String displayName;
                    private String aliasIdentityGroupId;
                    private PublicIdentityExtension3 extension;

                    @XmlElement(name = "DisplayName")
                    public String getDisplayName() {
                        return displayName;
                    }

                    public PublicIdentityExtension2 setDisplayName(final String displayName) {
                        this.displayName = displayName;
                        return this;
                    }

                    @XmlElement(name = "AliasIdentityGroupID")
                    public String getAliasIdentityGroupId() {
                        return aliasIdentityGroupId;
                    }

                    public PublicIdentityExtension2 setAliasIdentityGroupId(final String aliasIdentityGroupId) {
                        this.aliasIdentityGroupId = aliasIdentityGroupId;
                        return this;
                    }

                    @XmlElement(name = "Extension")
                    public PublicIdentityExtension3 getExtension() {
                        return extension;
                    }

                    public PublicIdentityExtension2 setExtension(
                            final PublicIdentityExtension3 extension) {
                        this.extension = extension;
                        return this;
                    }

                    public static class PublicIdentityExtension3 implements Serializable {

                        @Serial
                        private static final long serialVersionUID = 20230503163500L;

                        private String wildcardedImpu;
                        private String serviceLevelTraceInfo;
                        private Integer servicePriorityLevel;
                        private PublicIdentityExtension4 extension;

                        @XmlElement(name = "WildcardedIMPU")
                        public String getWildcardedImpu() {
                            return wildcardedImpu;
                        }

                        public PublicIdentityExtension3 setWildcardedImpu(final String wildcardedImpu) {
                            this.wildcardedImpu = wildcardedImpu;
                            return this;
                        }

                        @XmlElement(name = "ServiceLevelTraceInfo")
                        public String getServiceLevelTraceInfo() {
                            return serviceLevelTraceInfo;
                        }

                        public PublicIdentityExtension3 setServiceLevelTraceInfo(
                                final String serviceLevelTraceInfo) {
                            this.serviceLevelTraceInfo = serviceLevelTraceInfo;
                            return this;
                        }

                        @XmlElement(name = "ServicePriorityLevel")
                        public Integer getServicePriorityLevel() {
                            return servicePriorityLevel;
                        }

                        public PublicIdentityExtension3 setServicePriorityLevel(
                                final Integer servicePriorityLevel) {
                            this.servicePriorityLevel = servicePriorityLevel;
                            return this;
                        }

                        @XmlElement(name = "Extension")
                        public PublicIdentityExtension4 getExtension() {
                            return extension;
                        }

                        public PublicIdentityExtension3 setExtension(
                                final PublicIdentityExtension4 extension) {
                            this.extension = extension;
                            return this;
                        }

                        public static class PublicIdentityExtension4 implements Serializable {

                            @Serial
                            private static final long serialVersionUID = 20230503163500L;

                            private List<ExtendedPriority> extendedPriority;
                            private PublicIdentityExtension5 extension;

                            @XmlElement(name = "ExtendedPriority")
                            public List<ExtendedPriority> getExtendedPriority() {
                                return extendedPriority;
                            }

                            public PublicIdentityExtension4 setExtendedPriority(
                                    final List<ExtendedPriority> extendedPriority) {
                                this.extendedPriority = extendedPriority;
                                return this;
                            }

                            @XmlElement(name = "Extension")
                            public PublicIdentityExtension5 getExtension() {
                                return extension;
                            }

                            public PublicIdentityExtension4 setExtension(
                                    final PublicIdentityExtension5 extension) {
                                this.extension = extension;
                                return this;
                            }

                            public static class ExtendedPriority implements Serializable {

                                @Serial
                                private static final long serialVersionUID = 20230503163500L;

                                private String priorityNamespace;
                                private String priorityLevel;

                                @XmlElement(name = "PriorityNamespace")
                                public String getPriorityNamespace() {
                                    return priorityNamespace;
                                }

                                public ExtendedPriority setPriorityNamespace(final String priorityNamespace) {
                                    this.priorityNamespace = priorityNamespace;
                                    return this;
                                }

                                @XmlElement(name = "PriorityLevel")
                                public String getPriorityLevel() {
                                    return priorityLevel;
                                }

                                public ExtendedPriority setPriorityLevel(final String priorityLevel) {
                                    this.priorityLevel = priorityLevel;
                                    return this;
                                }
                            }

                            public static class PublicIdentityExtension5 implements Serializable {

                                @Serial
                                private static final long serialVersionUID = 20230503163500L;
                                private Integer maxNumOfAllowedSimultRegistrations;

                                @XmlElement(name = "MaxNumOfAllowedSimultRegistrations")
                                public Integer getMaxNumOfAllowedSimultRegistrations() {
                                    return maxNumOfAllowedSimultRegistrations;
                                }

                                public PublicIdentityExtension5 setMaxNumOfAllowedSimultRegistrations(
                                        final Integer maxNumOfAllowedSimultRegistrations) {
                                    this.maxNumOfAllowedSimultRegistrations =
                                            maxNumOfAllowedSimultRegistrations;
                                    return this;
                                }
                            }
                        }
                    }
                }
            }
        }

        public static class CoreNetworkServicesAuthorization implements Serializable {

            @Serial
            private static final long serialVersionUID = 20230503163500L;

            private Integer subscribedMediaProfileId;
            private CoreNetworkServicesAuthorizationExtension extension;

            @XmlElement(name = "SubscribedMediaProfileId")
            public Integer getSubscribedMediaProfileId() {
                return subscribedMediaProfileId;
            }

            public CoreNetworkServicesAuthorization setSubscribedMediaProfileId(
                    final Integer subscribedMediaProfileId) {
                this.subscribedMediaProfileId = subscribedMediaProfileId;
                return this;
            }

            @XmlElement(name = "Extension")
            public CoreNetworkServicesAuthorizationExtension getExtension() {
                return extension;
            }

            public CoreNetworkServicesAuthorization setExtension(
                    final CoreNetworkServicesAuthorizationExtension extension) {
                this.extension = extension;
                return this;
            }

            public static class CoreNetworkServicesAuthorizationExtension implements Serializable {

                @Serial
                private static final long serialVersionUID = 20230503163500L;

                private ListOfServiceIds listOfServiceIds;

                @XmlElement(name = "ListOfServiceIds")
                public ListOfServiceIds getListOfServiceIds() {
                    return listOfServiceIds;
                }

                public CoreNetworkServicesAuthorizationExtension setListOfServiceIds(
                        final ListOfServiceIds listOfServiceIds) {
                    this.listOfServiceIds = listOfServiceIds;
                    return this;
                }

                public static class ListOfServiceIds implements Serializable {

                    @Serial
                    private static final long serialVersionUID = 20230503163500L;
                    private List<String> serviceIds;

                    @XmlElement(name = "ServiceId")
                    public List<String> getServiceIds() {
                        return serviceIds;
                    }

                    public ListOfServiceIds setServiceIds(final List<String> serviceIds) {
                        this.serviceIds = serviceIds;
                        return this;
                    }
                }
            }
        }

        public static class InitialFilterCriteria implements Serializable {

            @Serial
            private static final long serialVersionUID = 20230503163500L;
            private Integer priority;
            private TriggerPoint triggerPoint;
            private ApplicationServer applicationServer;
            private Integer profilePartIndicator;

            @XmlElement(name = "Priority")
            public Integer getPriority() {
                return priority;
            }

            public InitialFilterCriteria setPriority(final Integer priority) {
                this.priority = priority;
                return this;
            }

            @XmlElement(name = "TriggerPoint")
            public TriggerPoint getTriggerPoint() {
                return triggerPoint;
            }

            public InitialFilterCriteria setTriggerPoint(
                    final TriggerPoint triggerPoint) {
                this.triggerPoint = triggerPoint;
                return this;
            }

            @XmlElement(name = "ApplicationServer")
            public ApplicationServer getApplicationServer() {
                return applicationServer;
            }

            public InitialFilterCriteria setApplicationServer(
                    final ApplicationServer applicationServer) {
                this.applicationServer = applicationServer;
                return this;
            }

            @XmlElement(name = "ProfilePartIndicator")
            public Integer getProfilePartIndicator() {
                return profilePartIndicator;
            }

            public InitialFilterCriteria setProfilePartIndicator(final Integer profilePartIndicator) {
                this.profilePartIndicator = profilePartIndicator;
                return this;
            }

            public static class TriggerPoint implements Serializable {

                @Serial
                private static final long serialVersionUID = 20230503163500L;
                private String conditionTypeCnf;
                private List<Spt> spt;

                @XmlElement(name = "ConditionTypeCNF")
                public String getConditionTypeCnf() {
                    return conditionTypeCnf;
                }

                public TriggerPoint setConditionTypeCnf(final String conditionTypeCnf) {
                    this.conditionTypeCnf = conditionTypeCnf;
                    return this;
                }

                @XmlElement(name = "SPT")
                public List<Spt> getSpt() {
                    return spt;
                }

                public TriggerPoint setSpt(
                        final List<Spt> spt) {
                    this.spt = spt;
                    return this;
                }

                public static class Spt implements Serializable {

                    @Serial
                    private static final long serialVersionUID = 20230503163500L;
                    private String conditionNegated;
                    private List<Integer> group;
                    private String requestUri;
                    private String method;
                    private SipHeader sipHeader;
                    private Integer sessionCase;
                    private SessionDescription sessionDescription;
                    private SePoTriExtension extension;

                    @XmlElement(name = "ConditionNegated")
                    public String getConditionNegated() {
                        return conditionNegated;
                    }

                    public Spt setConditionNegated(final String conditionNegated) {
                        this.conditionNegated = conditionNegated;
                        return this;
                    }

                    @XmlElement(name = "Group")
                    public List<Integer> getGroup() {
                        return group;
                    }

                    public Spt setGroup(final List<Integer> group) {
                        this.group = group;
                        return this;
                    }

                    @XmlElement(name = "RequestURI")
                    public String getRequestUri() {
                        return requestUri;
                    }

                    public Spt setRequestUri(final String requestUri) {
                        this.requestUri = requestUri;
                        return this;
                    }

                    @XmlElement(name = "Method")
                    public String getMethod() {
                        return method;
                    }

                    public Spt setMethod(final String method) {
                        this.method = method;
                        return this;
                    }

                    @XmlElement(name = "SipHeader")
                    public SipHeader getSipHeader() {
                        return sipHeader;
                    }

                    public Spt setSipHeader(
                            final SipHeader sipHeader) {
                        this.sipHeader = sipHeader;
                        return this;
                    }

                    @XmlElement(name = "SessionCase")
                    public Integer getSessionCase() {
                        return sessionCase;
                    }

                    public Spt setSessionCase(final Integer sessionCase) {
                        this.sessionCase = sessionCase;
                        return this;
                    }

                    @XmlElement(name = "SessionDescription")
                    public SessionDescription getSessionDescription() {
                        return sessionDescription;
                    }

                    public Spt setSessionDescription(
                            final SessionDescription sessionDescription) {
                        this.sessionDescription = sessionDescription;
                        return this;
                    }

                    @XmlElement(name = "Extension")
                    public SePoTriExtension getExtension() {
                        return extension;
                    }

                    public Spt setExtension(
                            final SePoTriExtension extension) {
                        this.extension = extension;
                        return this;
                    }

                    public static class SipHeader implements Serializable {

                        @Serial
                        private static final long serialVersionUID = 20230503163500L;
                        private String header;
                        private String content;

                        @XmlElement(name = "Header")
                        public String getHeader() {
                            return header;
                        }

                        public SipHeader setHeader(final String header) {
                            this.header = header;
                            return this;
                        }

                        @XmlElement(name = "Content")
                        public String getContent() {
                            return content;
                        }

                        public SipHeader setContent(final String content) {
                            this.content = content;
                            return this;
                        }
                    }

                    public static class SessionDescription implements Serializable {

                        @Serial
                        private static final long serialVersionUID = 20230503163500L;

                        private String line;
                        private String content;

                        @XmlElement(name = "Line")
                        public String getLine() {
                            return line;
                        }

                        public SessionDescription setLine(final String line) {
                            this.line = line;
                            return this;
                        }

                        @XmlElement(name = "Content")
                        public String getContent() {
                            return content;
                        }

                        public SessionDescription setContent(final String content) {
                            this.content = content;
                            return this;
                        }
                    }

                    public static class SePoTriExtension implements Serializable {

                        @Serial
                        private static final long serialVersionUID = 20230503163500L;
                        private List<Integer> registrationTypes;

                        @XmlElement(name = "RegistrationType")
                        public List<Integer> getRegistrationTypes() {
                            return registrationTypes;
                        }

                        public SePoTriExtension setRegistrationTypes(final List<Integer> registrationTypes) {
                            this.registrationTypes = registrationTypes;
                            return this;
                        }
                    }
                }
            }

            public static class ApplicationServer implements Serializable {

                @Serial
                private static final long serialVersionUID = 20230503163500L;
                private String serverName;
                private Integer defaultHandling;
                private String serviceInfo;
                // We do not support arbitrary XML, that's why we do not implement ApplicationServerExtension


                @XmlElement(name = "ServerName")
                public String getServerName() {
                    return serverName;
                }

                public ApplicationServer setServerName(final String serverName) {
                    this.serverName = serverName;
                    return this;
                }

                @XmlElement(name = "DefaultHandling")
                public Integer getDefaultHandling() {
                    return defaultHandling;
                }

                public ApplicationServer setDefaultHandling(final Integer defaultHandling) {
                    this.defaultHandling = defaultHandling;
                    return this;
                }

                @XmlElement(name = "ServiceInfo")
                public String getServiceInfo() {
                    return serviceInfo;
                }

                public ApplicationServer setServiceInfo(final String serviceInfo) {
                    this.serviceInfo = serviceInfo;
                    return this;
                }
            }
        }

        public static class ServiceProfileExtension implements Serializable {

            @Serial
            private static final long serialVersionUID = 20230503163500L;
            private List<Integer> sharedIfcSetId;

            @XmlElement(name = "SharedIFCSetID")
            public List<Integer> getSharedIfcSetId() {
                return sharedIfcSetId;
            }

            public ServiceProfileExtension setSharedIfcSetId(final List<Integer> sharedIfcSetId) {
                this.sharedIfcSetId = sharedIfcSetId;
                return this;
            }
        }
    }

    public static class ImsSubscriptionExtension implements Serializable {

        @Serial
        private static final long serialVersionUID = 20230503163500L;

        private String imsi;
        private ImsSubscriptionExtension2 extension;

        @XmlElement(name = "IMSI")
        public String getImsi() {
            return imsi;
        }

        public ImsSubscriptionExtension setImsi(final String imsi) {
            this.imsi = imsi;
            return this;
        }

        @XmlElement(name = "Extension")
        public ImsSubscriptionExtension2 getExtension() {
            return extension;
        }

        public ImsSubscriptionExtension setExtension(
                final ImsSubscriptionExtension2 extension) {
            this.extension = extension;
            return this;
        }

        public static class ImsSubscriptionExtension2 implements Serializable {

            @Serial
            private static final long serialVersionUID = 20230503163500L;
            private List<ReferenceLocationInformation> referenceLocationInformation;

            @XmlElement(name = "ReferenceLocationInformation")
            public List<ReferenceLocationInformation> getReferenceLocationInformation() {
                return referenceLocationInformation;
            }

            public ImsSubscriptionExtension2 setReferenceLocationInformation(
                    final List<ReferenceLocationInformation> referenceLocationInformation) {
                this.referenceLocationInformation = referenceLocationInformation;
                return this;
            }

            public static class ReferenceLocationInformation implements Serializable {

                @Serial
                private static final long serialVersionUID = 20230503163500L;
                private String accessType;
                private String accessInfo;
                private String accessValue;

                @XmlElement(name = "AccessType")
                public String getAccessType() {
                    return accessType;
                }

                public ReferenceLocationInformation setAccessType(final String accessType) {
                    this.accessType = accessType;
                    return this;
                }

                @XmlElement(name = "AccessInfo")
                public String getAccessInfo() {
                    return accessInfo;
                }

                public ReferenceLocationInformation setAccessInfo(final String accessInfo) {
                    this.accessInfo = accessInfo;
                    return this;
                }

                @XmlElement(name = "AccessValue")
                public String getAccessValue() {
                    return accessValue;
                }

                public ReferenceLocationInformation setAccessValue(final String accessValue) {
                    this.accessValue = accessValue;
                    return this;
                }
            }
        }
    }
}
