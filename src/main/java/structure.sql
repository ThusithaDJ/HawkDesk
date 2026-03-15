CREATE DATABASE `pharmacy`;

use Pharmacy;

CREATE TABLE `brands` (
  `brandId` int(11) NOT NULL AUTO_INCREMENT,
  `brandName` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`brandId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `category` (
  `catId` int(11) NOT NULL AUTO_INCREMENT,
  `categoryName` varchar(45) DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`catId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `grninfo` (
  `grnNo` int(11) NOT NULL AUTO_INCREMENT,
  `date` date DEFAULT NULL,
  `subTotal` double DEFAULT NULL,
  PRIMARY KEY (`grnNo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `invoiceinfo` (
  `invoiceNo` int(11) NOT NULL AUTO_INCREMENT,
  `date` date DEFAULT NULL,
  `total` double DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  `paid` double DEFAULT NULL,
  `discount` double DEFAULT NULL,
  PRIMARY KEY (`invoiceNo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `item` (
  `itemId` int(11) NOT NULL AUTO_INCREMENT,
  `brandId` int(11) NOT NULL,
  `catId` int(11) NOT NULL,
  `itemName` varchar(45) DEFAULT NULL,
  `minLevel` int(10) unsigned DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`itemId`),
  KEY `fk_item_brands_idx` (`brandId`),
  KEY `fk_item_category1_idx` (`catId`),
  CONSTRAINT `fk_item_brands` FOREIGN KEY (`brandId`) REFERENCES `brands` (`brandId`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_item_category1` FOREIGN KEY (`catId`) REFERENCES `category` (`catId`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  PRIMARY KEY (`stockId`),
  KEY `fk_stock_item1_idx` (`itemId`),
  KEY `fk_stock_grninfo1_idx` (`grnNo`),
  CONSTRAINT `fk_stock_grninfo1` FOREIGN KEY (`grnNo`) REFERENCES `grninfo` (`grnNo`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_stock_item1` FOREIGN KEY (`itemId`) REFERENCES `item` (`itemId`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `grn` (
  `grnNo` int(11) NOT NULL DEFAULT '0',
  `itemId` int(11) NOT NULL,
  `expireDate` date DEFAULT NULL,
  `itemQty` int(11) DEFAULT NULL,
  `itemCost` double DEFAULT NULL,
  `itemPrice` double DEFAULT NULL,
  `no` int(10) unsigned NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`no`),
  KEY `fk_grn_grnInfo1_idx` (`grnNo`),
  KEY `fk_grn_item1_idx` (`itemId`),
  CONSTRAINT `fk_grn_grnInfo1` FOREIGN KEY (`grnNo`) REFERENCES `grninfo` (`grnNo`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_grn_item1` FOREIGN KEY (`itemId`) REFERENCES `item` (`itemId`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `invoice` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `invoiceNo` int(11) NOT NULL,
  `itemId` int(11) NOT NULL,
  `stockId` int(11) NOT NULL,
  `batch` varchar(45) DEFAULT NULL,
  `dateTime` date DEFAULT NULL,
  `qty` int(11) DEFAULT NULL,
  `subTotal` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_invoice_item1_idx` (`itemId`),
  KEY `fk_invoice_stock1_idx` (`stockId`),
  KEY `fk_invoice_invoiceinfo1_idx` (`invoiceNo`),
  CONSTRAINT `fk_invoice_invoiceinfo1` FOREIGN KEY (`invoiceNo`) REFERENCES `invoiceinfo` (`invoiceNo`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_invoice_item1` FOREIGN KEY (`itemId`) REFERENCES `item` (`itemId`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_invoice_stock1` FOREIGN KEY (`stockId`) REFERENCES `stock` (`stockId`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `return` (
  `returnId` int(11) NOT NULL AUTO_INCREMENT,
  `invoiceNo` int(11) NOT NULL,
  `Qty` int(11) DEFAULT NULL,
  `reson` varchar(45) DEFAULT NULL,
  `returnTo` int(11) DEFAULT NULL,
  `stat` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`returnId`),
  KEY `fk_return_invoiceinfo1_idx` (`invoiceNo`),
  CONSTRAINT `fk_return_invoiceinfo1` FOREIGN KEY (`invoiceNo`) REFERENCES `invoiceinfo` (`invoiceNo`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
