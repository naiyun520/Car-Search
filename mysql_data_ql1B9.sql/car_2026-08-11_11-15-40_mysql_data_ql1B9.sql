-- MySQL dump 10.13  Distrib 5.7.44, for Linux (x86_64)
--
-- Host: localhost    Database: car
-- ------------------------------------------------------
-- Server version	5.7.44-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ci_admin`
--

DROP TABLE IF EXISTS `ci_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_admin` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `must_change_password` tinyint(4) NOT NULL DEFAULT '1',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_admin`
--

LOCK TABLES `ci_admin` WRITE;
/*!40000 ALTER TABLE `ci_admin` DISABLE KEYS */;
INSERT INTO `ci_admin` VALUES (1,'admin','$2y$10$ew19da3Ql1/koi0l03oH4.wOnETCLRIgaQ.hU5hZnjzzq.zrRuWPK',1,1,'2026-08-11 10:15:20','2026-08-07 23:12:28','2026-08-11 10:15:20');
/*!40000 ALTER TABLE `ci_admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_admin_login_attempt`
--

DROP TABLE IF EXISTS `ci_admin_login_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_admin_login_attempt` (
  `identity_hash` char(64) NOT NULL,
  `fail_count` int(10) unsigned NOT NULL DEFAULT '0',
  `locked_until` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`identity_hash`),
  KEY `idx_locked` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_admin_login_attempt`
--

LOCK TABLES `ci_admin_login_attempt` WRITE;
/*!40000 ALTER TABLE `ci_admin_login_attempt` DISABLE KEYS */;
/*!40000 ALTER TABLE `ci_admin_login_attempt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_admin_token`
--

DROP TABLE IF EXISTS `ci_admin_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_admin_token` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` int(10) unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_admin_token`
--

LOCK TABLES `ci_admin_token` WRITE;
/*!40000 ALTER TABLE `ci_admin_token` DISABLE KEYS */;
INSERT INTO `ci_admin_token` VALUES (1,1,'8b822432daeb6f2c459b6d4957de00278cd69d81f38947d3795e25cc624f6d6d','2026-08-08 07:12:36','2026-08-07 23:12:36'),(2,1,'b8a1c5dd74e9e97baade2aa0a45eb127411c07c41b0ce629fb785b3455b480ce','2026-08-08 20:56:24','2026-08-08 12:56:24'),(3,1,'e6ef023e232e43533559a45f95b1f73b4287f6065f94ced37951ffe64db013c3','2026-08-09 05:28:05','2026-08-08 21:28:05'),(4,1,'acfa33462cebb38434ce180b202ea7d2efde2c27d2d09767ec19c16896ab8b07','2026-08-10 00:54:25','2026-08-09 16:54:25'),(5,1,'1b7789951814eda860eb703e62412428c659cd4082797d7645ef065f30ebfaf1','2026-08-10 05:38:27','2026-08-09 21:38:27'),(6,1,'c619bffd1ab4d1423dae23fbf3b9362ed2be0f2b49390c55dfba61d7be86c367','2026-08-10 07:45:43','2026-08-09 23:45:43'),(7,1,'c1efa9b2788937ac51b93f0b893cd44ebfaffe4e7ec158d6af32ea489f0abbaa','2026-08-10 21:23:57','2026-08-10 13:23:57'),(8,1,'bd37012c12a33ae9d09ba6c613f1ebcff6b745f5bb019ddee2e93538676e2ba5','2026-08-10 21:45:22','2026-08-10 13:45:22'),(9,1,'b290fd34ce811fb16d662c520a8907f3b78245ad6ddc8e7ef8a1eb37379e40c2','2026-08-10 23:14:33','2026-08-10 15:14:33'),(10,1,'a28fe2cac20512568e60b0b9d7cb16728ae258cb5f3c1f17d346cb4855f89984','2026-08-11 00:40:06','2026-08-10 16:40:06'),(11,1,'60613d70bf97ace0598ab28086f1d9636777350432b2eb71bb857f64df5478ae','2026-08-11 08:46:06','2026-08-11 00:46:06'),(12,1,'5a02f4878d80efbc5dbec7ef3a559f111c6e6148217bcd37149e7912f56f28e4','2026-08-11 16:02:42','2026-08-11 08:02:42'),(13,1,'5a87ea5e1cedcfa4ee5eb09769e45efbd4474349b7c09398e1538d1c50a23717','2026-08-11 16:23:58','2026-08-11 08:23:58'),(14,1,'22f8829c5343f6cc004fb3b3c603892aad6940169ff16a94d64960987e5f2096','2026-08-11 16:24:09','2026-08-11 08:24:09'),(15,1,'cdafafdd43ce800f37391ef93960e68a20a9816eb5539051fc679d6c42440085','2026-08-11 18:15:20','2026-08-11 10:15:20');
/*!40000 ALTER TABLE `ci_admin_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_announcement`
--

DROP TABLE IF EXISTS `ci_announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_announcement` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `suppress_hours` int(10) unsigned NOT NULL DEFAULT '24',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_active` (`status`,`start_at`,`end_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_announcement`
--

LOCK TABLES `ci_announcement` WRITE;
/*!40000 ALTER TABLE `ci_announcement` DISABLE KEYS */;
INSERT INTO `ci_announcement` VALUES (1,'服务公告','你好',24,1,NULL,NULL,'2026-08-07 23:13:07','2026-08-07 23:13:07');
/*!40000 ALTER TABLE `ci_announcement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_audit_log`
--

DROP TABLE IF EXISTS `ci_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_audit_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` int(10) unsigned NOT NULL,
  `action` varchar(80) NOT NULL,
  `target_type` varchar(40) NOT NULL,
  `target_id` varchar(64) NOT NULL DEFAULT '',
  `detail` json DEFAULT NULL,
  `ip` varchar(45) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_created` (`admin_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_audit_log`
--

LOCK TABLES `ci_audit_log` WRITE;
/*!40000 ALTER TABLE `ci_audit_log` DISABLE KEYS */;
INSERT INTO `ci_audit_log` VALUES (1,1,'announcement.save','announcement','1','[]','117.152.200.103','2026-08-07 23:13:07'),(2,1,'settings.save','setting','','[\"customer_service_phone\", \"customer_service_hours\", \"privacy_retention_days\", \"disclaimer\"]','117.152.200.103','2026-08-07 23:13:17'),(3,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 0, \"payment_enabled\": false, \"updated_secrets\": [\"wechat_app_secret\"], \"wechat_offer_id\": \"\"}','117.152.200.103','2026-08-07 23:20:40'),(4,1,'service.update','service','1','[\"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]','117.152.200.103','2026-08-08 00:22:15'),(5,1,'service.update','service','6','[\"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]','117.152.200.103','2026-08-08 00:23:48'),(6,1,'service.update','service','5','[\"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]','117.152.200.103','2026-08-08 00:23:48'),(7,1,'service.update','service','8','[\"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]','117.152.200.103','2026-08-08 00:24:00'),(8,1,'feedback.reply','feedback','1','[]','117.152.200.103','2026-08-08 00:48:20'),(9,1,'user.status','user','1','{\"status\": 0}','117.152.200.103','2026-08-08 00:51:28'),(10,1,'user.status','user','1','{\"status\": 1}','117.152.200.103','2026-08-08 00:51:29'),(11,1,'feedback.reply','feedback','1','{\"status\": \"resolved\"}','117.152.200.103','2026-08-08 02:48:26'),(12,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": false, \"updated_secrets\": [\"wechat_virtual_app_key\"], \"wechat_offer_id\": \"gyzFXfm1VlRf2acT44ZQ3e3RvzQ4quCj\"}','117.152.200.103','2026-08-08 13:06:02'),(13,1,'settings.save','setting','','[\"customer_service_phone\", \"customer_service_hours\", \"privacy_retention_days\", \"disclaimer\"]','117.152.200.103','2026-08-08 13:13:18'),(14,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": false, \"updated_secrets\": [], \"wechat_offer_id\": \"gyzFXfm1VlRf2acT44ZQ3e3RvzQ4quCj\"}','117.152.200.103','2026-08-08 13:28:06'),(15,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": false, \"updated_secrets\": [], \"wechat_offer_id\": \"gyzFXfm1VlRf2acT44ZQ3e3RvzQ4quCj\"}','117.152.200.103','2026-08-08 13:30:22'),(16,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [\"wechat_sandbox_app_key\", \"wechat_production_app_key\"], \"wechat_offer_id\": \"gyzFXfm1VlRf2acT44ZQ3e3RvzQ4quCj\"}','117.152.200.103','2026-08-08 20:37:03'),(17,1,'service.update','service','5','{\"fields\": [\"code\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','117.152.200.103','2026-08-08 20:43:17'),(18,1,'service.update','service','5','{\"fields\": [\"code\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-08 20:43:32'),(19,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-09 21:39:19'),(20,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-09 21:44:07'),(21,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-09 23:44:35'),(22,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-09 23:57:43'),(23,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [\"wechat_sandbox_app_key\"], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-09 23:57:54'),(24,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [\"wechat_production_app_key\"], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-09 23:58:07'),(25,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:15:38'),(26,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:20:53'),(27,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:22:18'),(28,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:22:32'),(29,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:22:48'),(30,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:22:56'),(31,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-10 00:23:19'),(32,1,'service.update','service','1','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:26:32'),(33,1,'service.update','service','1','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:26:46'),(34,1,'service.update','service','2','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:28:25'),(35,1,'service.update','service','2','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:28:30'),(36,1,'service.update','service','3','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:29:59'),(37,1,'service.update','service','4','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:31:11'),(38,1,'service.update','service','6','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:35:00'),(39,1,'service.update','service','6','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:35:04'),(40,1,'service.update','service','7','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:36:27'),(41,1,'service.update','service','8','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:37:30'),(42,1,'service.update','service','8','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:38:07'),(43,1,'service.update','service','9','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:39:03'),(44,1,'service.update','service','10','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:40:08'),(45,1,'service.update','service','11','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"request_url_cipher\", \"updated_at\"]}','113.57.100.239','2026-08-10 13:41:45'),(46,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 0, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\"}','113.57.100.239','2026-08-10 15:28:53'),(47,1,'user.status','user','520','{\"status\": 0}','223.160.232.15','2026-08-10 15:30:58'),(48,1,'user.status','user','520','{\"status\": 1}','223.160.232.15','2026-08-10 15:31:00'),(49,1,'service.sort','service','','{\"ids\": [1, 2, 3, 5, 4, 6, 7, 8, 9, 10, 11]}','117.152.200.103','2026-08-11 00:33:01'),(50,1,'service.sort','service','','{\"ids\": [1, 2, 5, 3, 4, 6, 7, 8, 9, 10, 11]}','117.152.200.103','2026-08-11 00:33:03'),(51,1,'service.sort','service','','{\"ids\": [1, 5, 2, 3, 4, 6, 7, 8, 9, 10, 11]}','117.152.200.103','2026-08-11 00:33:04'),(52,1,'service.sort','service','','{\"ids\": [5, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11]}','117.152.200.103','2026-08-11 00:33:06'),(53,1,'service.update','service','5','{\"fields\": [\"code\", \"payment_product_id\", \"name\", \"short_name\", \"description\", \"icon\", \"input_schema\", \"result_schema\", \"request_method\", \"response_code_path\", \"response_success_value\", \"response_data_path\", \"sale_price\", \"cost_price\", \"sort\", \"status\", \"updated_at\"]}','117.152.200.103','2026-08-11 00:46:53'),(54,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-11 00:58:42'),(55,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-11 01:00:53'),(56,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 1, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\", \"wechat_sandbox_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-11 01:29:57'),(57,1,'order.batch_delete','order','2026081101310800000223B870','{\"count\": 6, \"order_nos\": [\"2026081101310800000223B870\", \"20260811013228000002106BCD\", \"20260811014045000002C3915D\", \"20260811014127000001A58B76\", \"202608110141330000010F50C8\", \"20260811014135000001D8F4D4\"]}','117.152.200.103','2026-08-11 07:44:07'),(58,1,'payment.settings.save','setting','payment','{\"wechat_app_id\": \"wxd965aa89d6c91045\", \"wechat_pay_env\": 0, \"payment_enabled\": true, \"updated_secrets\": [], \"wechat_offer_id\": \"1450613080\"}','117.152.200.103','2026-08-11 09:32:17'),(59,1,'order.batch_delete','order','20260811074714000002218BA1','{\"count\": 9, \"order_nos\": [\"20260811074714000002218BA1\", \"20260811080436000002C415C1\", \"202608110804390000028FC3F3\", \"20260811080555000002322880\", \"202608110806150000023061DE\", \"2026081108292500000267A777\", \"20260811082928000002AEC47F\", \"20260811094928000520A98508\", \"2026081109580900052094E245\"]}','117.152.200.103','2026-08-11 10:15:28'),(60,1,'order.batch_delete','order','202608111018300000014C0003','{\"count\": 1, \"order_nos\": [\"202608111018300000014C0003\"]}','117.152.200.103','2026-08-11 10:22:18');
/*!40000 ALTER TABLE `ci_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_feedback`
--

DROP TABLE IF EXISTS `ci_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_feedback` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) unsigned NOT NULL,
  `type` varchar(30) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `contact` varchar(100) NOT NULL DEFAULT '',
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `reply` varchar(1000) NOT NULL DEFAULT '',
  `replied_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_feedback`
--

LOCK TABLES `ci_feedback` WRITE;
/*!40000 ALTER TABLE `ci_feedback` DISABLE KEYS */;
INSERT INTO `ci_feedback` VALUES (1,1,'bug','111111','','resolved','OKOK','2026-08-08 02:48:26','2026-08-08 00:47:40','2026-08-08 02:48:26'),(2,1,'other','11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111','','pending','',NULL,'2026-08-08 02:54:41','2026-08-08 02:54:41');
/*!40000 ALTER TABLE `ci_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_order`
--

DROP TABLE IF EXISTS `ci_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_order` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL,
  `user_id` bigint(20) unsigned NOT NULL,
  `service_id` int(10) unsigned NOT NULL,
  `service_name` varchar(80) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `cost_amount` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `status` varchar(24) NOT NULL DEFAULT 'pending_payment',
  `input_cipher` text NOT NULL,
  `service_snapshot_cipher` text,
  `input_summary` varchar(100) NOT NULL DEFAULT '',
  `result_cipher` mediumtext,
  `provider_code` varchar(20) DEFAULT NULL,
  `provider_request_id` varchar(100) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `queried_at` datetime DEFAULT NULL,
  `expired_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_order`
--

LOCK TABLES `ci_order` WRITE;
/*!40000 ALTER TABLE `ci_order` DISABLE KEYS */;
INSERT INTO `ci_order` VALUES (54,'20260811111437000001055011',1,5,'车牌归属地查询',0.01,0.0000,'pending_payment','0vMOLR8KULKxvXkqzrnW9mprsRErED9e0Fz1V5PDQ4sOG+171a+3fqOff4ZR','OCIlSqon61aNQiTBY1JbcJYuqoSGi2+M5ufnipEBrlT/ajM2wf7oanmSXCCnxpxIpZGGJFZf5/chEG9Hh+CbU0yyEgwF6AcbEgwXKLgKY2SdQSXqqvr7MghRoAS8RsNiDimCpZcs7O8l9uLjg4aFcDa9Rx2NXe3lzeXxK5xZZRX7EWNf1OjQbf8M020qn39XDwZr6aGccsQhXsqLvIePMasmg0BvWa2bw7GKZonEB8PgEeQrTFV5YOpYeiRWuWLHZ8ltz8r/Dkitn3NyBOlwpglPK6Va+FAwxNDg5dgnAPCyL9K2Afc5bwXjfmlu2CaMDmqDs4qnPfSLFh1Hc1yL1X9ZYJEOoW8xb8hu4wEZ2tO7db7nP9wjvwJmmt5K/kO3xkUpUZJvmLqbHm5C1hPzDdIbrBP5Bf1VMCpg2scAlYwimwBF5Ywc59eN+n4nL+CJRbJiAzh3JeEeY73b01TU9A1Jjraskk1nBLEq91xM3xBFSK3M9Gq5ENT38LO9JYFDGcmQdCPEyCunaQvXsEZws1SdjWAMuzyKPKopssd3XdiPuelVhE8gVvlbiLcUSm2gRMO/9His3PQ9FVmwrAE8XM4Vx0RLYk7i3VekwjDvao7QY7lyj+48u+FApFX8u/xdUqKmPVCPrLTNSWMyKEb/E6nd/6leH5wU3rziTwoE0Ob1RzpkfiEiOCkUw82o6gf49WXt1gO9S+4Nv0AbDIuPb6CuEJinvsVYJ6EJbJ+C/i7GAJa2jS+GkIvTOGPq1cgjD02Urp1qUtz43cbp+EoG8O/cC8Og5ziYDHk6WNNkF3ccyk0ajIiOYShzfm+98hWfdiWx4PJsfmgIfxgzQ1qyDohrDDQkJcInjsabA9VqB08Jr5b81LDj5JZbBTY98NCMeSoujjAu+hqcn5L9we02rSvycDD9SZIkb9zfBnEhtrlFCQmSHV/Ep4+u7DJgtwGPeymuxHc3MN3sKXmdVpJbxzYQpdfrnBCIGD0zgrpoZj3i8I01A3kDKOMG9gDgXuagN3NJgPx+ohB2p9n1MTpsmuQDeS9WjhedqVCxazqkwD1H8pGoFhnRN119DwDjNz7NktOxs7yi/i3QmJrqp1hP+nyHtNOUj9KExcAlnSM+Zr9YhZ6nxwkE2K1aYI3NVO/RdvfAfl97e36gMaFFlpV9LZbT3zAAODkT4Z8Rl5ZNqY9QRyGS6Oz6qrMcT7e1cxW/QG4RHhjMTRxWlJnzqljuho972hpDKcxiCEf/xr8EBr4ZrnwK4AN2n3Ld7v3pDfKp3LYrujkwg7Yo/Ybo0rCRYOpKViAllCUV5DVG86w4nDUSRrvZn6F4n5jJ5bR3OeQai7DEK/As','鄂W***鄂W',NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:44:37','2026-08-11 11:14:37','2026-08-11 11:14:37'),(55,'20260811111439000001B07AAF',1,5,'车牌归属地查询',0.01,0.0000,'pending_payment','nzBE9zYHstchfW5TUZ3kDblggApYvkh4v+oI8qjq1am2j1kJ3MDDUdSx+iGC','qEOp2CiLeB3Org0O/0pt5bK/I+rYTn8oWZL+SQfLaYCrBO68EEVvLXoKkfwwddlJpSyJLVaHdfzLvYCxlydg+1rCOMoZvfAV7fQK5TG9SFa7zcnqoIltEHUbuzrxS7leJBJ/9oYdjHrzWOkC8knaPaJ9iqwumj6L0EUI5OCHQN2nmhy0/ktNc8z59f6R2jZAdCk5IcPamTbIxafUr3q/IseQcoudevk7UyPgc0XfSYfMdQyHCBg56M7EA3NKU9TjqdBn3MNn0gS9B7yUZ0L8gbU1J01tbMuVeq82+f0Hk6wL0d3B7LmldBTATEVvVbcd75X0yEwGfxIXfnTIbFJxOPznnxe1S5fjmtDfo6aFrfnkO+oteFcsEPlYsmDPemvJ+7srXp5Jr1WObsVvk1ERoprT2naIG4OZmQMfNICveooRrdIC/kmRFwey7jf/1tmN6+RJ1gasS5WXyTuKphm6RX2cry1JFLAM7zpWXS9eYMPQ1Mvymtbw8uef0SAwl1cDom16l9qV05hFk0RkpHTQJr9NzRFMr/IxfOIqZZX5Tkdkhcbom7dvFj/a6x9D4bALwoITm2iSYwOl658BefZGoYTdQGmzpaiVmRVVDo4nFPdbrB3wHLW1hNBX7I+S1erNDoZQ3aII8yrRa+9we5fTsnVbsP/DghW4g2ebjo4vlqgywlWG+3haArGTMCmQ/K+qq/eU2a4ZLgKdPV587RTNQyxOHj4MLsCsZlm9MyPWgYsgxEVmGSz4mXHZdRy8AksIxgKWqHB/bRDAAggGZxsiBBMrzEs1el0YlUVQ0suI9IWu+0gVVKAr8wiVAYSajjzHeUSq5VPHoxtnaAMGcUcG4X9pXVfd/+eNhxzFdlSxRq+KTB0yKSZnvqnga/BT0w376q5HtTuuqpBfOlHzImrG13KVsZaPanRMAONdaYUx3GSTZkc/r5ZgooQ2eJ4mBQ29qLDF6a/3RtsNnKOc6S0LoLMvZ85/JAzuaEaJSe4ehuR4JZaH3FphFmjGvTmPMYgOQRh+SIYSdCQj5AVo+8JRKkBpk1+zjCjATlzvMCR95lXUmED2fPCX+2wrx1puHs86wL9LURrQiCEFK2rfytx59g1vxrurg6akp05hff/LiEsNk2RPHNZ8kywdjhh+3FHgmEELdCIPpD7Opbo/DaGCBYZeNncnN2e8FIO0+IlhgRExIH7eiKqQlSUNKXxUc/2C2+wS5FN3+yyeNiyEOj3DMj87lAe7WlV/D2Lc0fuRqksqVYlgVbbkPyQL41Sif2Rn5E47cwViCKVm3xRPa3Rt/2nROBwuL8aZg6gOEPkWFHp/4UyrkBx8hQMLVGTnwPnZkEyJ2978','鄂W***鄂W',NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:44:39','2026-08-11 11:14:39','2026-08-11 11:14:39'),(56,'202608111114400000010820CC',1,5,'车牌归属地查询',0.01,0.0000,'pending_payment','S39O9XyfT52S6I2vfIB4lhC95MHyzmYQLsaEWW/7zffbGLCTG3TcpJ1NN6ks','nv4ex/dJVTlXMA9o+UsB5fVibYa36lUIwglyISg9kqebIi5iFgkiztEof5aHpH0trgHyihPWxsPebWV5P159JCy65J8/LLB2wjKSZVrC2oK765fYXmuKDRCPtOTkB3SBvZ5uUKZMlGY7HG6at3ZlzqT1fVBBkqPCnhVxVZtwHUvVAxwRY/8m8n9g0dhNjtesJyVCHZX3ORxE3jALrd79OL4030CLyYPdW62l+7bRg3qcQz7Hvx8dT77d8LQy9unw2toV3QiG6lLw/nKFM1Hej8gMU9L52TAKe/I0meL5b3gebBiILydLE2oLCSRHz4P/AfeoSGDlluuc38ug5k76qGHe+3bXAusXkO+dSBoH6Zeh/tDm/s0gekCaeOUfefubKNukTrFf2H8AKdhkfLn3Z6WnTaS2JOhr0W23hPJsOh45MVShDU3dOIJ92Nhz+EQ+R96UNg5+MLHpUrlhrFSNbpo/VOhvtW+MT6GhPm4tTTy7OLXWL6vFGqePv79eM+XvFt3zXypuK4TftRuaLL8wkMTPSQZLMmd+e2mKoQaRsALCpb32We+unVvfA1aiZpKwjXaQxqeX6+PfhQ+HXDH8W4gVq4xQdBWIJ2KXokrZ7NHZYDazreuFavUDP/gohnPJk8bz2oOxgpcaO0jKDlbJMI+PSBJuioVv0xzByKgemdFBRDW+P56uYmYEI9a/Qd8Pk6OSevw5P7KKwXxg9Mm0SZS+UA6VcFub2TN0WPtFgVIpgdmgCEaFmg3SA6tDQ6cIPZQr8NXovSZzlwRhtsLAlC7rqM4XxB5+fusAcjnwJNHhTRCAF7Ral7JB7tK3DulCO76LvRtJMCqryBIvNS46Pn1jV5jZtoaJG17QW7wuqSVO3dS1ZkS13eVy8cQBgV63CuZ0ibDyot0vu5qOirH4rFDwyTHpf8cEJg+5NEFlRoA1tOcZtzQU8vmKnjcfIGYfFOaYN3ZiN8+Lnn/7lb6QvnkrGfCzpS/ZPS0ppvs5IQccg9M3YYgeqCTW/BNwiQN3DfiJnZPMCZpds+7IhlexrXRNaXjFDLokBdOmQ1fpuAbhug/6OJGDI3x9ZY6KM0kRTU/HxrX8b/CQWQGDOHZVHdsUb5USRzhgNNoShftltNdnQdOmyen7lZuLNZ47+ov/E5TaBcre891IN6Q+TlBQaloVB0XeHNbn1aGFsB6aiqkwprOsLzisyNFfvXQjQSJhQUN1+BrSmfv2HVLATnqKEjkUenfkzxcg6p3HA4ByRuBjHRWfnFn9VTFVAuW6bkZGkkk0oOADiDgoxbl8NWrvikxX7Gh7fzQf8pyzldp/tUEEhXWlyax/n02khYgMEmCu0kRLUTxT','鄂W***鄂W',NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:44:40','2026-08-11 11:14:40','2026-08-11 11:14:40'),(57,'202608111114440000011E51EC',1,5,'车牌归属地查询',0.01,0.0000,'pending_payment','JBXD8nvGEQtsVbFVOotzXe652XD2eC9i53v+PVA1WrARUpCYw7eEXzPbvGhq','XMCpUommZjChpBIsLgk9dXgutzpPbZkwLHveU814hBuoZX4WLSpWX/qtHUT/fGRhwnbATAx7007JpUXJ8XK+OOIfHuP5MACU0Qt2KQrIOxS93btVrWKK0jn+qkBS1e2og16C5cdwZrEF39puk64ddElEJeIlHWkfYLIsdroLCOBa28BgTMGVilbXWahrAk9p00audR8kaoAT6RvM5hEFriX9DngUxkwmQILkAbYYWNjhZ4UKn24cSOjwJBPeaDuWGN7M7GH2Hp/EJzPsvMtVtVtDxs665+C74QAlBHlbHqkyyoOk3tuaGcopiB4iDibNDxfYmCguafx91QiIQ4yOVu5uBnkjRMVNEqnacin5Z6NPsjBfDrDESvzbdA1AJXV8AdI+2MZipS+C7gzL9NLksXk3g2rhu+aeHPtpxAc1lzvctYKthQU96ixgkpe9on9oojbFIWd9Q2iAdBYidqkaUF5gFOiB8plM/+fPJjn8NPZX5h6Ou/9R4YedWqZfBj3cJ3Oyh6eRbqPVgfZ4JO7e7qCpDJx0UtzLc55xiNTZb7fUXy+QvQ8omlW0vLbn5R9OkaQKDmzQxRSZmvwq12xZpc7S1khBBmYqR/OZECGLyIl3wrLbcV/L8pSBtfg9YqDAH/9QHGNZerST+Lp3N5nIeU30akx0kSUV1g2GhQW3N0Q2GHGSVg56ycZUx1Rj2NSE3go/Q6NiK/aWMGAxGZ7Y7436YVr5dhF7+EvQWF5oytJ+uUOKsNDMSZ9nXFWRixNlBG+BKXfsv8dK1iqmRw4bQDcyfY4Gsl471fbVoq8x6NMmW3xcSAtMYlMNI06SxbFDIrdIl+K+2o+mSBKPgi5FeZDxM3DRmjrdwzsyL8iOCVGTwDp0RX5Hwyu83TTNFccTZSYWN9jAvuV8xg3PnUBLPXIYtHF/VgsgGG4YmjvR+EVsbuxc0W8++Oc/etzsNlH7uZ80y3vLwbN4zlQDt3K6rX6nnhAfOfMnYpCWaTwcCltttHJqnIjLQRXKB53wv1FHW4imlHuh26Yu/Y/jagqJ1nNqDkbuMyf5fGILIcOhoN02IubcTF73mYkHmJ51NbqEAQoNmPjjye1Iunpeb+CRXRpN/LJHyRGmpi1m2ai92auV2Hzq5Skp5SVZ7IqhJV98RSht/yZo6A00GYE0W0QYuv7970gPRpXfIS3gazLbm5FBrZMqXk0BUZ549WcPAHE/Q5yI1LaxuvhPKKAFviptLM8SwX0tfNz1zSbGQOUQ+rS88XnJUhRkUr1Md19jGwit9/uyIc03TbEEKQH1M6vCF8G0YKmmHwPEndk9kzuo5XGqOSOPVBm5yctY5MGMFd+IUKkYEkyZ','鄂A***鄂A',NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:44:44','2026-08-11 11:14:44','2026-08-11 11:14:44'),(58,'20260811111445000001CD6B95',1,5,'车牌归属地查询',0.01,0.0000,'pending_payment','eykqW5ziipbqV/FBJskxA3ZMPFpe00WZ07IrMb9+5giy6/PtsQZO0QI7oQMG','WWDT5ZjmVUtBM0KGtpqGAoYOJt1fRX8xTugi02zHuX4egGBh9hSINFNuvKc2HgUo4o1LelQ/D9NDWabVs5iH1gOzD7JWbzWjPbJq+9E9DxvML5u6sLgJZ8xpB/XeNQkeeaZhVt9FxXp6c+AVJDMi6tN3H2bkjqH/TYTTVNBqv6+Ry/Of5unN8vWBv8tHwnMS9vX7/ueLKnzLomU1jaTnq8V0xDSTe5/1gq+0KRVkUklvIquXDcCVKoXQJ+VK6E6c6q2tFgIpjfwxhx9gMr1XJvJQb9dv5mlOXnCQzIZQ+5XyrWymOORDw107sEX1vu+ZH3njXQXm3B8+1QWWcEb9fTgoTmP1Nektpc6DeEsqG1JIub0otIDk77JwMHdD7S1D2yx6AUxzbp1AW21jRcxB9pBAw5V7rXKKFwXW5/1xEahfkk310+xYALZTSYA1ZVIY9F8D+m8dZoVWz66BRZEfJpyDA0glj3lc5LE/zXHzFSmk6AdiAxNCIgRTswwSZCf5F1vwhDIX8SZgSghxx9gxCTEwqY+04LKPqGcIZTIIVgxwVxEbO0BK0Gj8WWSple5uRMpT76GIW2h+mSG0O5zsk1Ure9mPdPgsFUb7PgZLayjzRfI5BWHOrdmUee9M8gO9J4Trh3bfGyUUY8OgZ76+fZMKbpEsBkSvYB8JAflt3J32ZPOaQA/nOWBnRoTD5SEbu5S669MEbLLexcZXMFPqbZtpSxEl9wX0pldT8LcuwlX6rrPaLvwFUW9sWPuIWWCRuNird6eJ/k6WC/hdL0SPnHcvMwGkOZYjyqXTwZIC8agpwUCAdNxHlNf1F2XcUW88bVWoPPMm/usIvtdgWL3l3KC7qUplAeEe0td+puEGIe4KRjcZ+zEu9wuCkMMIJ9/Q2sLFR4AQocZT2dHZCgck0d/ckjcPqLUjjP7S8BtAbQRXpLrsS84nd6qx1HT4ztcieaZQKV0OIQdKYuWIpipH+vTrPaXl+GksrKkk9uBSsGzIic/EC4uQyBwS2oepeVecE7Cz5H24Ec8fvEX2M0256SWy3saBz9gh4OTFysu7dU8d9pKdRnl49SpLzgrtPOUCxr+/dzQ97R4wkIbmTxIJB92DNNtgMFuodtllyLSaGCkbxP7/go60/n3zZM7bzp6vWVSPqTlpk+DOuYHax/OFQ+93XC48eET7xdcvc0DzCBsgMJ105b/cBzRjyV7AIqDw9gL/IttTAFbdHJJB0xwUVP5DWHGHRFbb68MG6spxrN/u5jCN26Jko4VOPTL9yivzsegr2DeF0zehI3TNcy5Lm5XRcqMNhgvYsfxNn9mYoA4G2BAEfjlry9QwpuAHEYlyC2rRXxYV','鄂A***鄂A',NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:44:45','2026-08-11 11:14:45','2026-08-11 11:14:45');
/*!40000 ALTER TABLE `ci_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_payment`
--

DROP TABLE IF EXISTS `ci_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_payment` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) unsigned NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `transaction_id` varchar(100) DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `pay_env` tinyint(4) NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'created',
  `callback_payload` text,
  `delivery_status` varchar(20) NOT NULL DEFAULT 'pending',
  `delivery_attempts` int(10) unsigned NOT NULL DEFAULT '0',
  `delivery_attempted_at` datetime DEFAULT NULL,
  `delivery_last_error` varchar(500) DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `refund_order_no` varchar(32) DEFAULT NULL,
  `refund_status` varchar(20) NOT NULL DEFAULT 'none',
  `refund_amount` decimal(10,2) DEFAULT NULL,
  `refund_reason` varchar(10) DEFAULT NULL,
  `refund_from_status` varchar(24) DEFAULT NULL,
  `refund_payload` text,
  `refund_last_error` varchar(500) DEFAULT NULL,
  `refund_requested_at` datetime DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  UNIQUE KEY `uk_transaction` (`transaction_id`),
  UNIQUE KEY `uk_refund_order_no` (`refund_order_no`),
  KEY `idx_delivery_status` (`delivery_status`,`delivery_attempted_at`),
  KEY `idx_refund_status` (`refund_status`,`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_payment`
--

LOCK TABLES `ci_payment` WRITE;
/*!40000 ALTER TABLE `ci_payment` DISABLE KEYS */;
INSERT INTO `ci_payment` VALUES (54,54,'20260811111437000001055011',NULL,0.01,0,'created',NULL,'pending',0,NULL,NULL,NULL,NULL,'none',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:14:37','2026-08-11 11:14:37'),(55,55,'20260811111439000001B07AAF',NULL,0.01,0,'created',NULL,'pending',0,NULL,NULL,NULL,NULL,'none',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:14:39','2026-08-11 11:14:39'),(56,56,'202608111114400000010820CC',NULL,0.01,0,'created',NULL,'pending',0,NULL,NULL,NULL,NULL,'none',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:14:40','2026-08-11 11:14:40'),(57,57,'202608111114440000011E51EC',NULL,0.01,0,'created',NULL,'pending',0,NULL,NULL,NULL,NULL,'none',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:14:44','2026-08-11 11:14:44'),(58,58,'20260811111445000001CD6B95',NULL,0.01,0,'created',NULL,'pending',0,NULL,NULL,NULL,NULL,'none',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-11 11:14:45','2026-08-11 11:14:45');
/*!40000 ALTER TABLE `ci_payment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_secure_setting`
--

DROP TABLE IF EXISTS `ci_secure_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_secure_setting` (
  `key` varchar(80) NOT NULL,
  `value_cipher` text NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_secure_setting`
--

LOCK TABLES `ci_secure_setting` WRITE;
/*!40000 ALTER TABLE `ci_secure_setting` DISABLE KEYS */;
INSERT INTO `ci_secure_setting` VALUES ('wechat_access_token','0SETsExl4IyovMPeIF++hZ36sIT4NEhgj5nvtwN/YRzZZ6/nd8BKYlqOPqYtTaHOgrnjjbeNyB7oqFugS2BOjpxDkHJl/dWfo4xjtPppVUpq5A9LCm6fDFxPVFbe60J6x0kNGgtqwm0jDObZ0Mlr65IsGFIPZp4Q81ZI0Lz3vdK/I3o5A7jGRzSRyTCyIT/TWBo9ZLN5pxw602l28yCsO703XRbXJNDyNS2nflBohSd1','2026-08-11 09:33:01'),('wechat_app_secret','NnkuSQCjxZ5SBDjC3Vanxnj6fQFn4gQkIGF2FC6LzTkrVh9QUTz60SI5QyzCd+9oJiFOijK04PS52ih8DKMwFF9W5uKMzgpT','2026-08-07 23:20:40'),('wechat_production_app_key','bjIaCwB0J1XgSqniorNpMd6RphRw6K5DF/TyJ8DnvOlnXijl5qpL+pVY8LvtQHeY8uU+IU6LCcxe9hhMwRrBEM04aZD9tBXf','2026-08-09 23:58:07'),('wechat_sandbox_app_key','NbH4yhykWxHsd82nfoZw0K4BEf22CRijODugdUGgNaNXVfAykHIq0UIUecM53wNZ2TYAlr0KN7YeX9nBug2pfw97+7mUUfSq','2026-08-09 23:57:54'),('wechat_virtual_app_key','bsszAdCkJ1orubqnQbZWDJnGXDLL1/54zQrZt3mcubzncYOhRJHCb6XAd5v3I0sJRN8WzmxZQfPtH01voHLyVfayVegLo1pC','2026-08-08 13:06:02');
/*!40000 ALTER TABLE `ci_secure_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_service`
--

DROP TABLE IF EXISTS `ci_service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_service` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL,
  `payment_product_id` varchar(128) NOT NULL DEFAULT '',
  `provider_api_id` int(10) unsigned NOT NULL,
  `name` varchar(80) NOT NULL,
  `short_name` varchar(40) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `icon` varchar(20) NOT NULL DEFAULT 'car',
  `input_schema` json NOT NULL,
  `result_schema` json NOT NULL,
  `provider_key_cipher` text NOT NULL,
  `request_url_cipher` text,
  `request_method` varchar(10) NOT NULL DEFAULT 'GET',
  `response_code_path` varchar(100) NOT NULL DEFAULT 'code',
  `response_success_value` varchar(50) NOT NULL DEFAULT '200',
  `response_data_path` varchar(100) NOT NULL DEFAULT 'data',
  `sale_price` decimal(10,2) NOT NULL,
  `cost_price` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `sort` int(11) NOT NULL DEFAULT '0',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0维护 1运行 2隐藏',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status_sort` (`status`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_service`
--

LOCK TABLES `ci_service` WRITE;
/*!40000 ALTER TABLE `ci_service` DISABLE KEYS */;
INSERT INTO `ci_service` VALUES (1,'plate_basic','plate_basic',2,'车牌五项信息','车牌五项','查询品牌、VIN、车辆种类、初登日期及使用性质','car','[{\"key\": \"chepai\", \"type\": \"plate\", \"label\": \"车牌号\", \"required\": true}]','{\"vin\": \"VIN车架号\", \"date\": \"初登日期\", \"name\": \"品牌名称\", \"type\": \"车辆种类\", \"usage\": \"使用性质\", \"model_no\": \"车辆型号\", \"engine_no\": \"发动机号\"}','wYJUYV/1MSNrqMZZ85RHOGFtxlXIc0iIC4A/RSb6ywrvZfrgoCs=','CEUDJe54z0teAYR9p6L2CfSUzOaNQmbietWOkzSssHWQapaqyQsyLzBbmfWb+4aTnWMdxV7YGHl72UW61aZMAbfs5hg4Tj1S8j1dlFM1WBkku/ZMkLu5znsHU98LS7sVlteTwpGR/PiARYmBn08=','GET','code','200','data',25.00,0.4500,2,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(2,'vin_decode','vin_decode',11,'VIN车辆信息解析','VIN解析','通过17位VIN解析车型配置与动力参数','car','[{\"key\": \"vin\", \"type\": \"vin\", \"label\": \"VIN车架号\", \"required\": true}]','{\"vin\": \"VIN车架号\", \"door_num\": \"车门数量\", \"pailiang\": \"车辆排量\", \"seat_num\": \"座位数量\", \"drivemode\": \"驱动方式\", \"fuel_type\": \"燃油类型\", \"gear_type\": \"变速箱类型\", \"max_power\": \"最大功率\", \"name_info\": \"汽车型号\", \"fuelmethod\": \"供油方式\", \"enginemodel\": \"发动机型号\", \"cylinder_num\": \"气缸数量\", \"market_price\": \"市场价格\", \"max_horsepower\": \"最大马力\", \"environmentalstandards\": \"排放标准\"}','OK0414YSjj2r3ZfYtLJ5u4y/S0eI6/yNqgrOXAAQp+z5c9q4xu0=','lFpWI8kJY1EysjDIZWwXVxdHcHsYPCqZGJ0Tp6N7wSDZhp77mPuiIj6iNBRmIv+6z1b2kTRZvekufRAWo6kM/UQvX0kxtWs7rYMeqhmDqDoE/aAb8/KaZ2zuD5ny+vhndJdsgICazPHGvjCfnh7p','GET','code','200','data',25.00,0.0430,3,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(3,'plate_full','plate_full',7,'车牌综合信息','综合车况','一次查询车辆基础信息与车型配置','car','[{\"key\": \"chepai\", \"type\": \"plate\", \"label\": \"车牌号\", \"required\": true}]','{\"vin\": \"VIN车架号\", \"date\": \"初登日期\", \"name\": \"品牌名称\", \"type\": \"车辆种类\", \"usage\": \"使用性质\", \"door_num\": \"车门数量\", \"model_no\": \"车辆型号\", \"pailiang\": \"车辆排量\", \"seat_num\": \"座位数量\", \"drivemode\": \"驱动方式\", \"engine_no\": \"发动机号\", \"fuel_type\": \"燃油类型\", \"gear_type\": \"变速箱类型\", \"max_power\": \"最大功率\", \"name_info\": \"汽车型号\", \"fuelmethod\": \"供油方式\", \"enginemodel\": \"发动机型号\", \"cylinder_num\": \"气缸数量\", \"market_price\": \"市场价格\", \"max_horsepower\": \"最大马力\", \"environmentalstandards\": \"排放标准\"}','59Pph2t7Vkuh8aNgbmZDiJ4j3d/khaqzTLodOHkgtJI7XUhTRcw=','fKH6ArB8bWkqC2QUNW/+X6wk3NQhI8I6JNBtK2g5URlvq4XhLkRcL4xY4wYir5TJeMpKqXlmJ4UK9A2TnMKnlU4qgWyIOOOgN7MGW0/ZXs8uUmGg9JNmL5xllCXZ9izid7kJScrGBH9qmbg6I1A=','GET','code','200','data',25.00,0.4800,4,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(4,'etc_owner_verify','etc_owner',39,'ETC人车关系核验','ETC核验','核验指定人员是否为指定车辆ETC所有人','car','[{\"key\": \"name\", \"type\": \"name\", \"label\": \"姓名\", \"required\": true}, {\"key\": \"chepai\", \"type\": \"plate\", \"label\": \"车牌号\", \"required\": true}]','{\"status\": \"核验结果\"}','ornNQ8XcpGZQmkJffH3tV5AqT4MT7KIbdfW5rhxgpzDQS8cXtKQ=','Kvaw+e6PS/PKqLMid4izSPVAN1PQOMotTfYo2/byhHpIO1JicqbR+/HDu6f5kfRrPJCXASHJrM//VJGMg2bkK2bwtr7NK27On6V6x15EbszE4PncFi7AXNjS52METzCc5QIWNcYuEVFSEcCkVt3a43WqCaNfWfDRjtkz','GET','code','200','data',25.00,1.4000,5,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(5,'plate_location','plate_location',22,'车牌归属地查询','归属地','查询车牌号对应地区，支持输入车牌前两位','car','[{\"key\": \"chepai\", \"type\": \"plate_prefix\", \"label\": \"车牌号或前两位\", \"required\": true}]','{\"chepai\": \"车牌号\", \"location\": \"归属地\"}','nBl6jUyca8b8r1kXXNkhYulEm1u23yQ/QgFUarq2FVgLiTn4Pq8=','XvLYHDJJ71Rxw5jMNlEAd3V6lk7BJ/ca/gB30rUPEJtFtyJh6+xEB30ndogUg22GhxvgMAutsDeCwr/PWuRtpsun7tOnkV9bf9NcPmA8ZTXTIzO2K90FIlixQgcwPrO9Zvk7e3FVlcO771taZqj9Gu0T0Wst/i8ckhvMw1gGDuwv//M5z+r6NDxYSSZn7lRuGoIIaA==','GET','code','200','data',0.01,0.0000,1,1,'2026-08-07 23:12:28','2026-08-11 00:46:53'),(6,'transfer_count','transfer_count',29,'车辆过户次数','过户次数','通过车牌号或VIN查询车辆过户次数','car','[{\"key\": \"value\", \"type\": \"plate_or_vin\", \"label\": \"车牌号或VIN\", \"required\": true}]','{\"value\": \"查询对象\", \"guohu_num\": \"过户次数\"}','41Pdt/uKQDhKRcj9wJLMhNLGGMLPK9bKKVTGFFGNbcei8E3o/P4=','27q7kU0GKEtVN9UbjVqAAW8M+UEO9pq+Ui5NXeEY82aOTvP5bAos5TB0/jOm72wdn9Lpm4tE+xr8t32qWZDTwDnkN4f3RBEf7frSpXvBVXGh1C9siiI5n7vyc/k5pOsRacA//qMxBF0fWydOt9WfuhCTa55xIQVXpWieXsM=','GET','code','200','data',25.00,3.5000,6,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(7,'owned_vehicle_count','owned_vehicle',30,'名下车辆数量','名下车辆','通过证件号码或统一社会信用代码查询名下车辆数','car','[{\"key\": \"value\", \"type\": \"identity\", \"label\": \"身份证号/公司社会信用代码\", \"required\": true}]','{\"mingxia_num\": \"名下车总数\"}','G4y657Nur8DSz6W98Ek9Gukd6XVfZFvkz5NMiZHJShSFjWFEcb8=','f32h3wwE3PBtIcAgN8J33Np+5sS/Y8P0gS21f1QAhtxOaM05AIPxyMO4zAYzSc3EpeCzsL+XErVAyAS32kQpMkVWuxTWCsiaeiD9xqcwvf1qKaEDi6iDPdYb5XNAHZ5FEdj+BvWwWQ/1glnX2hHFntNFy/IANtb3014IvijnDYpPCyr13T1aRfKdlg==','GET','code','200','data',25.00,4.5000,7,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(8,'owned_etc_count','owned_etc',42,'名下ETC车辆','名下ETC','通过证件号码查询名下ETC车辆总数','car','[{\"key\": \"value\", \"type\": \"idcard\", \"label\": \"证件号码\", \"required\": true}]','{\"list\": \"车辆列表\", \"mingxia_num\": \"名下ETC数\"}','61IOhHaqPlHyTpq9AqD8QKOkzwvHW0G42PZJ40QBK0dgmdPkvMU=','bDHb76kuS3QzhIPUzoyrrFbqI4ehXRY/zQ3uO/eKUeisJjM/MHELkXx3nvH4EMhDRvd3SZ9IcLYvGWT7MR+GjlDx/Ylqnk0OwkVXMChIuGQHRhSjtGghIa/YqtOV1/eFzfTlSyGJWhnEistAuZc+QKo=','GET','code','200','data',25.00,2.4000,8,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(9,'owner_verify_precise','owner_verify',31,'精准人车关系核验','精准核验','核验姓名与车牌号或VIN是否匹配','car','[{\"key\": \"name\", \"type\": \"name\", \"label\": \"姓名\", \"required\": true}, {\"key\": \"chepai\", \"type\": \"plate_or_vin\", \"label\": \"车牌号或vin车架号\", \"required\": true}]','{\"state\": \"核验结果\"}','7kGY+oMGSCVCiRXiSdOtwCkKnY8qAuJKSwMesV22YtS72kbHjm4=','cDU4j/U8CzQXTzV2hw7+AFSQGXDTLlSGxNIPdImgYQ/PeWVyJfq0/f6OdFhz9kUq019pP8sV2ox2qjZiuELPZK8UeB097+kaIHrkWydXvfuqjtjdqCFLB1VWQsEQb+f7SqDQHsz/ghBiDS7IQnW3ZeMRYM4r7+rlv/ekhWEEad8r4ID6tJULd6Fv','GET','code','200','data',25.00,3.4000,9,1,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(10,'owner_verify','',41,'人车关系核验','人车核验','核验姓名与车牌号是否匹配','car','[{\"key\": \"name\", \"type\": \"name\", \"label\": \"姓名\", \"required\": true}, {\"key\": \"chepai\", \"type\": \"plate\", \"label\": \"车牌号\", \"required\": true}]','{\"state\": \"核验结果\"}','YtjPnQAUzuSe+BjEd5hIyM2G4yw3UW0QkzEtZNYhsc9V6u6FmNM=',NULL,'GET','code','200','data',4.90,1.9000,10,2,'2026-08-07 23:12:28','2026-08-11 00:33:06'),(11,'insurance_dates','insurance_dates',34,'交强险投保日期','投保日期','查询车辆初次及最新交强险投保时间','car','[{\"key\": \"value\", \"type\": \"plate_or_vin\", \"label\": \"车牌号或vin车架号\", \"required\": true}]','{\"value\": \"查询对象\", \"new_date\": \"最新上险时间\", \"old_date\": \"初次上险时间\"}','P+X6Ksv8IcBIlrnKOh8k+ixwXQclvlH8F4hPPEjQrfO5KlTBNlM=','zcwfRr2X44GN/5RDJ279zjSQRuWSw7gFy4jx73usPIfidh0gi6zw32PCmyus4qO32gS0nEoZt5L40wFii7npxv5I6jPU2OkKztUbTxNsM4zhTirBrCRb2Pzz5ffW1IF7iGp1Wt7slkGSIDZ7aMIXw8A+z6xoV3U2u1fMBFE=','GET','code','200','data',25.00,2.3000,11,1,'2026-08-07 23:12:28','2026-08-11 00:33:06');
/*!40000 ALTER TABLE `ci_service` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_setting`
--

DROP TABLE IF EXISTS `ci_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_setting` (
  `key` varchar(80) NOT NULL,
  `value` text NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_setting`
--

LOCK TABLES `ci_setting` WRITE;
/*!40000 ALTER TABLE `ci_setting` DISABLE KEYS */;
INSERT INTO `ci_setting` VALUES ('customer_service_hours','工作日 09:00-18:00','2026-08-08 13:13:18'),('customer_service_phone','400-000-0000','2026-08-08 13:13:18'),('disclaimer','查询结果来自依法授权的数据服务，仅供本人合法用途参考。用户须确保已取得相关主体授权，禁止用于骚扰、歧视、非法调查或其他违法用途。因数据源更新存在延迟，结果不作为行政、司法或交易决策的唯一依据。','2026-08-08 13:13:18'),('payment_enabled','1','2026-08-11 09:32:17'),('privacy_retention_days','30','2026-08-08 13:13:18'),('wechat_access_token_expires_at','1786419182','2026-08-11 09:33:02'),('wechat_app_id','wxd965aa89d6c91045','2026-08-11 09:32:17'),('wechat_offer_id','1450613080','2026-08-11 09:32:17'),('wechat_pay_env','0','2026-08-11 09:32:17'),('wechat_sandbox_offer_id','1450613080','2026-08-11 01:29:57');
/*!40000 ALTER TABLE `ci_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_user`
--

DROP TABLE IF EXISTS `ci_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_user` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `unionid` varchar(64) DEFAULT NULL,
  `nickname` varchar(64) NOT NULL DEFAULT '微信用户',
  `avatar_url` varchar(500) NOT NULL DEFAULT '',
  `phone` varchar(32) NOT NULL DEFAULT '',
  `session_key_cipher` text,
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_status_created` (`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=521 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_user`
--

LOCK TABLES `ci_user` WRITE;
/*!40000 ALTER TABLE `ci_user` DISABLE KEYS */;
INSERT INTO `ci_user` VALUES (1,'ozTTlxcpQ53zgspgYD-AdpidLTV8',NULL,'微信用户','','','Cj76Q1KJ8wF5uwtBh9gjVMiACx/0VZz1Obi6J5hBUQiC0OPO0EB6VZ8EsYd9bec5i9hiD0H7h29hVjHF/irKHg==',1,'2026-08-08 21:29:45','2026-08-08 00:06:30','2026-08-08 21:29:45'),(2,'ozTTlxZLqoQSS4qQLNB8xL5LJ_NY',NULL,'微信用户','','','VZn7MAD0ecNFs/5VaLugI2pAi4NSCbCUgEMjxQsVfaNYhcFvJsoyXU8QkhBOSi5forBsqmoXQasr4s3xJFE4iA==',1,'2026-08-09 23:46:24','2026-08-09 22:36:25','2026-08-09 23:46:24'),(520,'ozTTlxQCoTRY9-EJ5ON5HtRYgudE',NULL,'微信用户','','','Io3o8Fjv1A/ku6H353vnc+kEB+HJy7zFn7dgXbPiB437QnbWGEIDWZl3zsdzDEs9X/iHBLN26HLKVP+7n+/Y3KA=',1,'2026-08-10 15:32:50','2026-08-10 15:22:31','2026-08-10 15:32:50');
/*!40000 ALTER TABLE `ci_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ci_user_token`
--

DROP TABLE IF EXISTS `ci_user_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ci_user_token` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ci_user_token`
--

LOCK TABLES `ci_user_token` WRITE;
/*!40000 ALTER TABLE `ci_user_token` DISABLE KEYS */;
INSERT INTO `ci_user_token` VALUES (2,1,'4eb3ee62824c1c8d6cd0e363785c7dc169cc64a79ad4dda234a8403f3a13fd72','2026-09-07 20:44:03','2026-08-08 20:44:03'),(3,1,'3124c2e734a2f8f0c432e5c959798f197b0b593c0d56ea697deebdb20b9cccc5','2026-09-07 21:28:56','2026-08-08 21:28:56'),(4,1,'ebbd115aa584109c7a5269549db2587d896191b2e60713b32f835d3fe6c77aac','2026-09-07 21:29:45','2026-08-08 21:29:45'),(5,2,'116e98ede22f005d06668a46b5e4eb4e27e03c912b4159c2173854c898e374ce','2026-09-08 22:36:25','2026-08-09 22:36:25'),(6,2,'321d86dbfe03bc70f74acb42fcd04739198482fc2378b32b51fb61f63b1fefdd','2026-09-08 23:46:24','2026-08-09 23:46:24'),(7,520,'0e43a3e07825de6f09aa448e5630e8d55f65cf81215b3327ec007bfd83f7d92f','2026-09-09 15:22:31','2026-08-10 15:22:31'),(8,520,'bb688c359bf4500e9be7e3d6f207f8428486f1156a450c187b697cf6361b0d7d','2026-09-09 15:32:50','2026-08-10 15:32:50');
/*!40000 ALTER TABLE `ci_user_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'car'
--

--
-- Dumping routines for database 'car'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-11 11:15:40
