CREATE TABLE IF NOT EXISTS `location_ip_sm_gw` (
  `imsiId` int(11) NOT NULL,
  `ipSmGwName` varchar(255) NOT NULL,
  `ipSmGwRealm` varchar(255) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`imsiId`),
  KEY `location_ip_sm_gw_name_index` (`ipSmGwName`),
  CONSTRAINT `location_ip_sm_gw_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Stores the IP-SM-GW (Serving-Node from SAR) assigned to the IMSI';
