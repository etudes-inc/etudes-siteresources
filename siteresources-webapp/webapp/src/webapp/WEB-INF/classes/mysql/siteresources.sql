--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/siteresources/trunk/siteresources-webapp/webapp/src/webapp/WEB-INF/classes/mysql/siteresources.sql $
-- $Id: siteresources.sql 6599 2013-12-12 20:21:39Z ggolden $
--**********************************************************************************
--
-- Copyright (c) 2013 Etudes, Inc.
-- 
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
--*********************************************************************************/

-----------------------------------------------------------------------------
-- SiteResources DDL
-----------------------------------------------------------------------------

CREATE TABLE SITE_RESOURCES
(
	ID				BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    UPLOAD_DATE		BIGINT NOT NULL,
    STORAGE_PATH	VARCHAR (255) NOT NULL,
    FILE_SIZE		BIGINT UNSIGNED NOT NULL,
    MIME_TYPE		VARCHAR (255) NOT NULL,
    PRIMARY KEY	(ID)
);

CREATE TABLE SITE_RESOURCE_PLACEMENT
(
	ID				BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
	RESOURCE_ID		BIGINT UNSIGNED NOT NULL,
    SITE_ID			VARCHAR (99) NOT NULL,
    NAME			VARCHAR (255) NOT NULL,
    PRIMARY KEY	(ID),
   	KEY SRP_KEY_NAME (SITE_ID, NAME),
   	KEY SRP_KEY_RESOURCE (RESOURCE_ID)
);

CREATE TABLE SITE_RESOURCE_REFERENCE
(
	ID				BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    SITE_ID			VARCHAR (99) NOT NULL,
    TOOL			VARCHAR (99) NOT NULL,
    TOOL_ID			BIGINT UNSIGNED NULL,
    NAME			VARCHAR (255) NOT NULL,
    PRIMARY KEY	(ID),
   	KEY SRR_KEY_REF (SITE_ID, TOOL, TOOL_ID)
);
