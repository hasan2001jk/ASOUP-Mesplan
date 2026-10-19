-- Схема базы mesplan на 198.1.2.246 (SQL Server 9.0.1399 = SQL Server 2005),
-- восстановлена из кэша IntelliJ (.idea/dataSources, снимок 22.01.2025 17:48).
-- Сгенерировано скриптом, руками не править; при появлении скрипта из SSMS — заменить им.
-- ⚠ УСТАРЕЛ. Действующая схема — docs/schema.sql (выгрузка из SSMS 19.10.2026):
-- после снимка добавлены header и strnazn, ftype расширен до 4 знаков.
--
-- ⚠ Расхождения с сущностями Java на момент снимка:
--   orders.strbazn  — в Java @Column(name = "strnazn");
--   orders.header   — колонки нет, а в Java поле header есть;
--   orders.ftype    — varchar(2), а форма «Ввод» предлагает PLAN / PL_E / PL_K.
-- База могла измениться после снимка — сверить запросом из docs/АНАЛИЗ-И-ПЛАН.md.

CREATE TABLE [dbo].[rodvag] (
    [code] varchar(2) NOT NULL,
    [symb] varchar(10) NOT NULL,
    [name] varchar(100) NOT NULL,
    CONSTRAINT [PK__rodvag__060DEAE8] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[przsob] (
    [code] int NOT NULL,
    [name] varchar(100) NOT NULL,
    [comment] varchar(255) NOT NULL,
    CONSTRAINT [PK__przsob__0EA330E9] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[conttypesize] (
    [code] int NOT NULL,
    [name] varchar(100) NULL,
    [nameshort] varchar(30) NULL,
    [length] int NULL,
    [brutto] float NULL,
    CONSTRAINT [PK__conttypesize__0AD2A005] PRIMARY KEY CLUSTERED ([code])
);
GO

CREATE TABLE [dbo].[orders] (
    [id] int IDENTITY(1,1) NOT NULL,
    [dateoper] datetime NOT NULL,
    [adm] int NOT NULL,
    [ftype] varchar(2) NOT NULL,
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
    [strbazn] int NOT NULL,
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
    CONSTRAINT [PK__orders__7C8480AE] PRIMARY KEY CLUSTERED ([id])
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
    CONSTRAINT [PK__orderdetails__7E6CC920] PRIMARY KEY CLUSTERED ([id])
);
GO

ALTER TABLE [dbo].[orderdetails] ADD CONSTRAINT [details_foreign] FOREIGN KEY ([orderid]) REFERENCES [dbo].[orders] ([id]);
GO
