-- =========================================================================
-- VIETNAMESE ADMINISTRATIVE UNITS SCHEMA (Microsoft SQL Server)
-- Tables: administrative_regions, administrative_units, provinces, districts, wards
-- =========================================================================

USE [PawnMasterDB];
GO

-- 1. CREATE administrative_regions TABLE
IF OBJECT_ID(N'dbo.administrative_regions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_regions (
        id INT NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NOT NULL,
        code_name VARCHAR(255) NULL,
        code_name_en VARCHAR(255) NULL,
        CONSTRAINT administrative_regions_pkey PRIMARY KEY (id)
    );
END
GO

-- 2. CREATE administrative_units TABLE
IF OBJECT_ID(N'dbo.administrative_units', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_units (
        id INT NOT NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        short_name NVARCHAR(255) NULL,
        short_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        code_name_en VARCHAR(255) NULL,
        CONSTRAINT administrative_units_pkey PRIMARY KEY (id)
    );
END
GO

-- 3. CREATE provinces TABLE
IF OBJECT_ID(N'dbo.provinces', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.provinces (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NOT NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        administrative_unit_id INT NULL,
        administrative_region_id INT NULL,
        CONSTRAINT provinces_pkey PRIMARY KEY (code),
        CONSTRAINT provinces_administrative_region_id_fkey FOREIGN KEY (administrative_region_id) REFERENCES dbo.administrative_regions(id),
        CONSTRAINT provinces_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_provinces_region' AND object_id = OBJECT_ID(N'dbo.provinces'))
    CREATE INDEX idx_provinces_region ON dbo.provinces(administrative_region_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_provinces_unit' AND object_id = OBJECT_ID(N'dbo.provinces'))
    CREATE INDEX idx_provinces_unit ON dbo.provinces(administrative_unit_id);
GO

-- 4. CREATE districts TABLE
IF OBJECT_ID(N'dbo.districts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.districts (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        province_code VARCHAR(20) NULL,
        administrative_unit_id INT NULL,
        CONSTRAINT districts_pkey PRIMARY KEY (code),
        CONSTRAINT districts_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id),
        CONSTRAINT districts_province_code_fkey FOREIGN KEY (province_code) REFERENCES dbo.provinces(code)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_districts_province' AND object_id = OBJECT_ID(N'dbo.districts'))
    CREATE INDEX idx_districts_province ON dbo.districts(province_code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_districts_unit' AND object_id = OBJECT_ID(N'dbo.districts'))
    CREATE INDEX idx_districts_unit ON dbo.districts(administrative_unit_id);
GO

-- 5. CREATE wards TABLE
IF OBJECT_ID(N'dbo.wards', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.wards (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        district_code VARCHAR(20) NULL,
        administrative_unit_id INT NULL,
        CONSTRAINT wards_pkey PRIMARY KEY (code),
        CONSTRAINT wards_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id),
        CONSTRAINT wards_district_code_fkey FOREIGN KEY (district_code) REFERENCES dbo.districts(code)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_wards_district' AND object_id = OBJECT_ID(N'dbo.wards'))
    CREATE INDEX idx_wards_district ON dbo.wards(district_code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_wards_unit' AND object_id = OBJECT_ID(N'dbo.wards'))
    CREATE INDEX idx_wards_unit ON dbo.wards(administrative_unit_id);
GO


