-- Схема базы mesplan на 198.1.2.246 — ДЕЙСТВУЮЩАЯ (INFORMATION_SCHEMA, выгружено
-- пользователем в SSMS 19.10.2026). Порядок колонок — как в базе.
--
-- Отличия от снимка IntelliJ 22.01.2025 (docs/schema-2025-01-22.sql): после снимка
-- колонки изменены, в конце таблицы orders — header varchar(50) NULL, strnazn int
-- (вместо strbazn) и ftype varchar(4) (было varchar(2)). Сущность Order отображена
-- на ЭТУ схему; стенд строится по ней.
--
-- Ключи и связь orderdetails → orders в выгрузке не показаны; взяты из снимка
-- (PK по id/code, FK details_foreign). IDENTITY у id — тоже из снимка.

CREATE TABLE [dbo].[rodvag] (
    [code] varchar(2) NOT NULL,
    [symb] varchar(10) NOT NULL,
    [name] varchar(100) NOT NULL,
    CONSTRAINT [PK_rodvag] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[przsob] (
    [code] int NOT NULL,
    [name] varchar(100) NOT NULL,
    [comment] varchar(255) NOT NULL,
    CONSTRAINT [PK_przsob] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[conttypesize] (
    [code] int NOT NULL,
    [name] varchar(100) NULL,
    [nameshort] varchar(30) NULL,
    [length] int NULL,
    [brutto] float NULL,
    CONSTRAINT [PK_conttypesize] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[orders] (
    [id] int IDENTITY(1,1) NOT NULL,
    [dateoper] datetime NOT NULL,
    [adm] int NOT NULL,
    [strview] varchar(3) NOT NULL,
    [datepogr] datetime NOT NULL,
    [transid] int NOT NULL,
    [gosotpr] int NOT NULL,
    [orderno] varchar(10) NOT NULL,
    [strorderno] int NOT NULL,
    [istrorderno] int NULL,
    [codengr] int NOT NULL,
    [otprname] varchar(50) NOT NULL,
    [poluchname] varchar(50) NOT NULL,
    [stotpr] int NOT NULL,
    [stnazn] int NOT NULL,
    [etsng] int NOT NULL,
    [gng] varchar(12) NOT NULL,
    [codegrpolport] varchar(20) NULL,
    [namegrpolport] varchar(50) NULL,
    [codeport] varchar(50) NULL,
    [rodvag] int NULL,
    [prinadl] tinyint NOT NULL,
    [netto] int NOT NULL,
    [vags] int NOT NULL,
    [prim] varchar(255) NULL,
    [rodotpr] tinyint NOT NULL,
    [otprcode] int NULL,
    [poluchcode] int NULL,
    [rezerv1] varchar(20) NULL,
    [rezerv2] varchar(20) NULL,
    [rezerv3] varchar(255) NULL,
    [containers] int NULL,
    [conttr] int NULL,
    [contpr] tinyint NULL,
    [admcount] tinyint NOT NULL,
    [header] varchar(50) NULL,
    [strnazn] int NOT NULL,
    [ftype] varchar(4) NOT NULL,
    CONSTRAINT [PK_orders] PRIMARY KEY CLUSTERED ([id])
);
GO

CREATE TABLE [dbo].[orderdetails] (
    [id] int IDENTITY(1,1) NOT NULL,
    [orderid] int NOT NULL,
    [adm] int NOT NULL,
    [gate] int NOT NULL,
    [expname] varchar(50) NOT NULL,
    [expcode] int NOT NULL,
    [info1] varchar(4) NULL,
    [info2] varchar(50) NULL,
    [result] tinyint NULL,
    [denyreason] varchar(100) NULL,
    CONSTRAINT [PK_orderdetails] PRIMARY KEY CLUSTERED ([id])
);
GO

ALTER TABLE [dbo].[orderdetails] ADD CONSTRAINT [details_foreign] FOREIGN KEY ([orderid]) REFERENCES [dbo].[orders] ([id]);
GO
