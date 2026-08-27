// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import com.cs.core.exec.logger.DebugExecuteLogger;
import com.cs.core.util.*;
import com.cs.template.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Module(DbVarModule.VAR_NAME)
public class DbVarModule implements VarModuleInterface {

    protected static final String VAR_NAME = "db";

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private ProductTypeEnum productType;
    private Map<String, Object> params;
    private Function<String, String> converter;
    private boolean printSqlLog;
    private boolean dollarSubstitutionAllowed;

    public DbVarModule(DataSource dataSource, ProductTypeEnum productType, Map<String, Object> params,
                       NamingStrategyEnum strategy, boolean printSqlLog) {
        this(dataSource, productType, params, strategy, printSqlLog, true);
    }

    public DbVarModule(DataSource dataSource, ProductTypeEnum productType, Map<String, Object> params,
                       NamingStrategyEnum strategy, boolean printSqlLog, boolean dollarSubstitutionAllowed) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.productType = productType;
        this.params = params;
        this.converter = Optional.ofNullable(strategy).orElse(NamingStrategyEnum.NONE).getFunction();
        this.printSqlLog = printSqlLog;
        this.dollarSubstitutionAllowed = dollarSubstitutionAllowed;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    private SqlMeta processTemplate(XmlSqlTemplate template) {
        try {
            return template.process(params, dollarSubstitutionAllowed);
        } catch (DollarSubstitutionException e) {
            log.warn("Blocked script db SQL template with dollar substitution: {}", e.getMessage());
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "api.sql.dollar.forbidden");
        }
    }

    private String getPageSql(String sql, int page, int size) {
        if (!productType.isMultiDialect()) {
            return productType.getPageSql(sql, page, size);
        }
        try (Connection connection = dataSource.getConnection()) {
            return PageSqlUtils.getPageSql(productType, connection, sql, page, size);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> build(Map<String, Object> row) {
        return Optional.ofNullable(row).map(r -> ConvertUtils.to(r, converter)).orElse(null);
    }

    private List<Map<String, Object>> build(List<Map<String, Object>> rows) {
        return rows.stream().map(this::build).collect(Collectors.toList());
    }

    @Comment("comment.db.selectAll")
    public List<Map<String, Object>> selectAll(@Comment("comment.param.sqlOrXml") String sqlOrXml) throws SQLException {
        if (printSqlLog) {
            log.info("Enter selectAll() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        long start = System.currentTimeMillis();
        try {
            return build(jdbcTemplate.queryForList(sqlMeta.getSql(), sqlMeta.getParameter().toArray()));
        } finally {
            DebugExecuteLogger.add(sqlMeta.getSql(), sqlMeta.getParameter(), System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.selectCount")
    public Integer selectCount(@Comment("comment.param.sqlOrXml") String sqlOrXml) {
        if (printSqlLog) {
            log.info("Enter selectCount() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        String countSql = String.format("select count(*) from (%s) a", sqlMeta.getSql());
        long start = System.currentTimeMillis();
        try {
            return jdbcTemplate.queryForObject(countSql, Integer.class, sqlMeta.getParameter().toArray());
        } finally {
            DebugExecuteLogger.add(countSql, sqlMeta.getParameter(), System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.selectOne")
    public Map<String, Object> selectOne(@Comment("comment.param.sqlOrXml") String sqlOrXml) {
        if (printSqlLog) {
            log.info("Enter selectOne() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        long start = System.currentTimeMillis();
        try {
            return build(jdbcTemplate
                    .query(sqlMeta.getSql(), new ResultSetExtractor<Map<String, Object>>() {
                                private ColumnMapRowMapper mapper = new ColumnMapRowMapper();

                                @Override
                                public Map<String, Object> extractData(ResultSet rs) throws SQLException, DataAccessException {
                                    if (rs.next()) {
                                        return mapper.mapRow(rs, 0);
                                    }
                                    return null;
                                }
                            },
                            sqlMeta.getParameter().toArray()));
        } finally {
            DebugExecuteLogger.add(sqlMeta.getSql(), sqlMeta.getParameter(), System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.page")
    public List<Map<String, Object>> page(@Comment("comment.param.sqlOrXml") String sqlOrXml)
            throws SQLException {
        if (printSqlLog) {
            log.info("Enter page() function, SQL:{},params:{}", sqlOrXml, params);
        }
        int page = PageSizeUtils.getPageFromParams(params);
        int size = PageSizeUtils.getSizeFromParams(params);
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        String pageSql = getPageSql(sqlMeta.getSql(), page, size);
        List<Object> parameters = sqlMeta.getParameter();
        this.productType.getPageConsumer().accept(page, size, parameters);
        long start = System.currentTimeMillis();
        try {
            return build(jdbcTemplate.queryForList(pageSql, parameters.toArray()));
        } finally {
            DebugExecuteLogger.add(pageSql, parameters, System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.insert")
    public Map<String, Object> insert(@Comment("comment.param.sqlOrXml") String sqlOrXml) {
        if (printSqlLog) {
            log.info("Enter insert() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        List<Object> parameters = sqlMeta.getParameter();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.update(
                    connection -> {
                        PreparedStatement ps = connection.prepareStatement(sqlMeta.getSql(), Statement.RETURN_GENERATED_KEYS);
                        new ArgumentPreparedStatementSetter(parameters.toArray()).setValues(ps);
                        return ps;
                    },
                    keyHolder);
            return build(keyHolder.getKeys());
        } finally {
            DebugExecuteLogger.add(sqlMeta.getSql(), parameters, System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.update")
    public int update(@Comment("comment.param.sqlOrXml") String sqlOrXml) {
        if (printSqlLog) {
            log.info("Enter update() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        List<Object> parameters = sqlMeta.getParameter();
        long start = System.currentTimeMillis();
        try {
            return jdbcTemplate.update(sqlMeta.getSql(), parameters.toArray());
        } finally {
            DebugExecuteLogger.add(sqlMeta.getSql(), parameters, System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.batchUpdate")
    public int batchUpdate(@Comment("comment.param.sqlList") List<String> sqlList) {
        if (printSqlLog) {
            log.info("Enter batchUpdate() function, SQL:{},params:{}", sqlList);
        }
        long start = System.currentTimeMillis();
        try {
            return Arrays.stream(jdbcTemplate.batchUpdate(sqlList.toArray(new String[0]))).sum();
        } finally {
            DebugExecuteLogger.add(sqlList.stream().collect(Collectors.joining(";")),
                    Collections.emptyList(), System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.delete")
    public int delete(@Comment("comment.param.sqlOrXml") String sqlOrXml) {
        if (printSqlLog) {
            log.info("Enter update() function, SQL:{},params:{}", sqlOrXml, params);
        }
        XmlSqlTemplate template = new XmlSqlTemplate(sqlOrXml);
        SqlMeta sqlMeta = processTemplate(template);
        List<Object> parameters = sqlMeta.getParameter();
        long start = System.currentTimeMillis();
        try {
            return jdbcTemplate.update(sqlMeta.getSql(), parameters.toArray());
        } finally {
            DebugExecuteLogger.add(sqlMeta.getSql(), parameters, System.currentTimeMillis() - start);
        }
    }

    @Comment("comment.db.beginTx")
    public TxVarModule beginTx() {
        return new TxVarModule(dataSource);
    }

}

