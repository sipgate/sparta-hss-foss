-- MySQL dump of the sparta-hss schema

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!50717 SELECT COUNT(*) INTO @rocksdb_has_p_s_session_variables FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'performance_schema' AND TABLE_NAME = 'session_variables' */;
/*!50717 SET @rocksdb_get_is_supported = IF (@rocksdb_has_p_s_session_variables, 'SELECT COUNT(*) INTO @rocksdb_is_supported FROM performance_schema.session_variables WHERE VARIABLE_NAME=\'rocksdb_bulk_load\'', 'SELECT 0') */;
/*!50717 PREPARE s FROM @rocksdb_get_is_supported */;
/*!50717 EXECUTE s */;
/*!50717 DEALLOCATE PREPARE s */;
/*!50717 SET @rocksdb_enable_bulk_load = IF (@rocksdb_is_supported, 'SET SESSION rocksdb_bulk_load = 1', 'SET @rocksdb_dummy_bulk_load = 0') */;
/*!50717 PREPARE s FROM @rocksdb_enable_bulk_load */;
/*!50717 EXECUTE s */;
/*!50717 DEALLOCATE PREPARE s */;

--
-- Table structure for table `imsi`
--

DROP TABLE IF EXISTS `imsi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imsi` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `imsi` char(15) NOT NULL,
  `simId` int(11) unsigned DEFAULT NULL,
  `state` enum('inactive','pending','active','active_deprecated','obsolete') NOT NULL DEFAULT 'inactive',
  `type` enum('main','dyn') NOT NULL,
  `assignedAt` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_imsi` (`imsi`),
  KEY `ix_sim` (`simId`),
  CONSTRAINT `fk_imsi_sim` FOREIGN KEY (`simId`) REFERENCES `sim` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=548985 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `imsi_profile`
--

DROP TABLE IF EXISTS `imsi_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imsi_profile` (
  `imsiId` int(11) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`imsiId`),
  CONSTRAINT `imsi_profile_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `imsi_scscf`
--

DROP TABLE IF EXISTS `imsi_scscf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imsi_scscf` (
  `imsiId` int(11) NOT NULL,
  `scscf` varchar(255) NOT NULL,
  `diameter_host` varchar(255) NOT NULL,
  `diameter_realm` varchar(255) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`imsiId`),
  KEY `imsi_scscf_scscf_index` (`scscf`),
  CONSTRAINT `imsi_scscf_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Stores the S-SCSF assigned to the IMSI';
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `location_vowifi`
--

DROP TABLE IF EXISTS `location_vowifi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `location_vowifi` (
  `imsiId` int(11) NOT NULL,
  `aaa_server_name` varchar(255) NOT NULL,
  `diameter_host` varchar(255) NOT NULL,
  `diameter_realm` varchar(255) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`imsiId`),
  KEY `location_vowifi_aaa_index` (`aaa_server_name`),
  CONSTRAINT `location_vowifi_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Stores the 3GPP AAA Server assigned to the IMSI';
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `location_lte`
--

DROP TABLE IF EXISTS `location_lte`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `location_lte` (
  `imsiId` int(11) NOT NULL,
  `mmeHostname` varchar(255) NOT NULL,
  `mmeRealm` varchar(255) NOT NULL,
  `visitedPlmnId` varchar(255) DEFAULT NULL,
  `tac` varchar(8) DEFAULT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`imsiId`),
  CONSTRAINT `location_lte_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Stores the current location (MME) assigned to the IMSI in 4G LTE';
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `location_ip_sm_gw`
--

DROP TABLE IF EXISTS `location_ip_sm_gw`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `location_ip_sm_gw` (
  `imsiId` int(11) NOT NULL,
  `ipSmGwName` varchar(255) NOT NULL,
  `ipSmGwRealm` varchar(255) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`imsiId`),
  KEY `location_ip_sm_gw_name_index` (`ipSmGwName`),
  CONSTRAINT `location_ip_sm_gw_imsi_id_fk` FOREIGN KEY (`imsiId`) REFERENCES `imsi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Stores the IP-SM-GW (Serving-Node from SAR) assigned to the IMSI';
/*!40101 SET character_set_client = @saved_cs_client */;


--
--
-- Table structure for table `msisdn`
--

DROP TABLE IF EXISTS `msisdn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `msisdn` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `msisdn` varchar(45) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_msisdn` (`msisdn`)
) ENGINE=InnoDB AUTO_INCREMENT=356139 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `sim`
--

DROP TABLE IF EXISTS `sim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sim` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `msisdn` int(10) unsigned NOT NULL,
  `secretKey` binary(16) NOT NULL,
  `sqn` bigint(20) DEFAULT '0',
  `op` binary(16) NOT NULL,
  `ss_cw` enum('active','inactive') DEFAULT 'active',
  `iccid` char(20) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `uix_msisdn` (`msisdn`) USING BTREE,
  KEY `iccid` (`iccid`),
  CONSTRAINT `fk_imsi_msisdn` FOREIGN KEY (`msisdn`) REFERENCES `msisdn` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=230412 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `tac_profile`
--

DROP TABLE IF EXISTS `tac_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tac_profile` (
  `tac` varchar(8) NOT NULL,
  `name` varchar(255) NOT NULL,
  `lastUpdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tac`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;


--
--
-- Table structure for table `roaming_blocked_imsi_override`
--

DROP TABLE IF EXISTS `roaming_blocked_imsi_override`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roaming_blocked_imsi_override` (
    `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    `imsi` char(20) NOT NULL,
    `gt_prefix` varchar(20) NOT NULL,
    `roaming` char(10) NOT NULL, -- ('allowed','blocked','default')
    `reason` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_imsi_gt_prefix` (`imsi`,`gt_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `roaming_blocked_imsi_override_lte`
--

DROP TABLE IF EXISTS `roaming_blocked_imsi_override_lte`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roaming_blocked_imsi_override_lte` (
    `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    `imsi` char(20) NOT NULL,
    `mcc` varchar(3) NOT NULL,
    `mnc` varchar(3) NOT NULL,
    `roaming` char(10) NOT NULL, -- ('allowed','blocked','default')
    `reason` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
UNIQUE KEY `uix_imsi_mcc_mnc` (`imsi`,`mcc`,`mnc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `roaming_blocked_location`
--

DROP TABLE IF EXISTS `roaming_blocked_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roaming_blocked_location` (
    `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    `gt_prefix` varchar(20) NOT NULL,
    `reason` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_gt_prefix` (`gt_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `roaming_blocked_location_lte`
--

DROP TABLE IF EXISTS `roaming_blocked_location_lte`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roaming_blocked_location_lte` (
    `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    `mcc` varchar(3) NOT NULL,
    `mnc` varchar(3) NOT NULL,
    `reason` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_mcc_mnc` (`mcc`,`mnc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
--
-- Table structure for table `roaming_blocked_event`
--

DROP TABLE IF EXISTS `roaming_blocked_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roaming_blocked_event` (
    `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
    `imsi` char(20) NOT NULL,
    `mcc` varchar(20) NOT NULL,
    `component` varchar(20) NOT NULL DEFAULT 'hss',
    `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_imsi` (`imsi`),
    KEY `ix_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
