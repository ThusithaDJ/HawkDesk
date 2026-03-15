
CREATE DATABASE `pharmacy`;

USE `pharmacy`;


CREATE TABLE `brands` (
  `brandId` int(11) NOT NULL AUTO_INCREMENT,
  `brandName` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`brandId`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8;


CREATE TABLE `category` (
  `catId` int(11) NOT NULL AUTO_INCREMENT,
  `categoryName` varchar(45) DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`catId`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8;



CREATE TABLE `grn` (
  `grnNo` int(11) NOT NULL DEFAULT '0',
  `itemId` int(11) NOT NULL,
  `expireDate` date DEFAULT NULL,
  `itemQty` int(11) DEFAULT NULL,
  `itemCost` double DEFAULT NULL,
  `itemPrice` double DEFAULT NULL,
  `no` int(10) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`no`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8;


CREATE TABLE `grninfo` (
  `grnNo` int(11) NOT NULL AUTO_INCREMENT,
  `date` date DEFAULT NULL,
  `subTotal` double DEFAULT NULL,
  PRIMARY KEY (`grnNo`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8;



CREATE TABLE `invoice` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `invoiceNo` int(11) NOT NULL,
  `itemId` int(11) NOT NULL,
  `stockId` int(11) NOT NULL,
  `batch` varchar(45) DEFAULT NULL,
  `dateTime` date DEFAULT NULL,
  `qty` int(11) DEFAULT NULL,
  `subTotal` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8;


CREATE TABLE `invoiceinfo` (
  `invoiceNo` int(11) NOT NULL AUTO_INCREMENT,
  `date` date DEFAULT NULL,
  `total` double DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  `paid` double DEFAULT NULL,
  `discount` double DEFAULT NULL,
  PRIMARY KEY (`invoiceNo`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8;

CREATE TABLE `item` (
  `itemId` int(11) NOT NULL AUTO_INCREMENT,
  `brandId` int(11) NOT NULL,
  `catId` int(11) NOT NULL,
  `itemName` varchar(45) DEFAULT NULL,
  `minLevel` int(10) unsigned DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`itemId`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8;



CREATE TABLE `return` (
  `returnId` int(11) NOT NULL AUTO_INCREMENT,
  `invoiceNo` int(11) NOT NULL,
  `Qty` int(11) DEFAULT NULL,
  `reson` varchar(45) DEFAULT NULL,
  `returnTo` int(11) DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`returnId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `stock` (
  `stockId` int(11) NOT NULL AUTO_INCREMENT,
  `itemId` int(11) NOT NULL,
  `grnNo` int(11) NOT NULL,
  `batch` varchar(45) DEFAULT NULL,
  `expireDate` date DEFAULT NULL,
  `qty` int(11) DEFAULT NULL,
  `cost` double DEFAULT NULL,
  `price` double DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`stockId`)
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8;
