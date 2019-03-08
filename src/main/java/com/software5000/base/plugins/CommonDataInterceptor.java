package com.software5000.base.plugins;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * @author 605162215@qq.com
 * @date 2016-06-23
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class CommonDataInterceptor implements Interceptor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static final ThreadLocal<Long> IGNORE_DATA = new ThreadLocal<Long>();

    private Properties props = null;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (IGNORE_DATA.get() == null) {
            processIntercept(invocation);
        } else {
            IGNORE_DATA.remove();
        }
        return invocation.proceed();
    }

    public Object processIntercept(Invocation invocation) throws Throwable {
        String interceptMethod = invocation.getMethod().getName();
        if (!"prepare".equals(interceptMethod)) {
            return invocation.proceed();
        }

        StatementHandler handler = (StatementHandler) PluginUtil.processTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(handler);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        SqlCommandType sqlCmdType = ms.getSqlCommandType();
        if (sqlCmdType != SqlCommandType.UPDATE && sqlCmdType != SqlCommandType.INSERT) {
            return invocation.proceed();
        }
        //获取配置参数
        String createDateColumn, updateDateColumn;

        createDateColumn = "createTime";
        updateDateColumn = "updateTime";


        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        Object parameterObject = boundSql.getParameterObject();
        //获取原始sql
        String originalSql = (String) metaObject.getValue("delegate.boundSql.sql");
        logger.debug("==> originalSql: " + originalSql);
        //追加参数
        String newSql = "";
        if (sqlCmdType == SqlCommandType.UPDATE && updateDateColumn.length() > 0) {
            newSql = changeSqlForDate(originalSql);
        } else if (sqlCmdType == SqlCommandType.INSERT && createDateColumn.length() > 0) {
            newSql = changeInsertData(originalSql);
        }
        //修改原始sql
        if (newSql.length() > 0) {
            logger.debug("==> newSql after change create/update time : " + newSql);
            metaObject.setValue("delegate.boundSql.sql", newSql);
        }
        return invocation.proceed();
    }

    private String changeInsertData(String sqls) {

        int createTimeIndex = -1;
        int updateTimeIndex = -1;
        try {
            Statement stmt = CCJSqlParserUtil.parse(sqls);
            Insert insert = (Insert) stmt;
            List<Column> columns = insert.getColumns();
            for (int ci = 0; ci < columns.size(); ci++) {
                if ("createTime".equalsIgnoreCase(columns.get(ci).getColumnName())) {
                    createTimeIndex = ci;
                }
                if ("updateTime".equalsIgnoreCase(columns.get(ci).getColumnName())) {
                    updateTimeIndex = ci;
                }

                if (createTimeIndex > -1 && updateTimeIndex > -1) {
                    break;
                }
            }
            ItemsList itemList = insert.getItemsList();
            if (itemList instanceof ExpressionList) {
                ((ExpressionList) itemList).getExpressions().set(createTimeIndex, new StringValue((TIMESTAMP_FORMAT.format(new Date()))));
                ((ExpressionList) itemList).getExpressions().set(updateTimeIndex, new StringValue((TIMESTAMP_FORMAT.format(new Date()))));
            } else if (itemList instanceof MultiExpressionList) {
                for (ExpressionList el : ((MultiExpressionList) itemList).getExprList()) {
                    el.getExpressions().set(createTimeIndex, new StringValue((TIMESTAMP_FORMAT.format(new Date()))));
                    el.getExpressions().set(updateTimeIndex, new StringValue((TIMESTAMP_FORMAT.format(new Date()))));
                }
            }
            return insert.toString();
        } catch (JSQLParserException e) {
            logger.error("change insert sql date error!", e);
        }


        return null;
    }

    private String changeSqlForDate(String sqls) {
        StringBuilder sb = new StringBuilder();
        // 可能是多个sql
        for (String sql : sqls.split(";")) {
            // 简单来，直接在where之前增加一个updateTime的时间设置
            if (sql.toLowerCase().indexOf(" set ") != -1) {
                sql = sql.replaceAll("(?i) set ", " SET updateTime = '" + TIMESTAMP_FORMAT.format(new Date()) + "',");
            } else {
                sql = sql + ",updateTime = '" + TIMESTAMP_FORMAT.format(new Date()) + "'";
            }
            sb.append(sql);
            if (sb.toString().length() > 0) {
                sb.append(";");
            }
        }

        return sb.toString();
    }

    private boolean contains(List<Column> columns, String columnName) {
        if (columns == null || columns.size() <= 0) {
            return false;
        }
        if (columnName == null || columnName.length() <= 0) {
            return false;
        }
        for (Column column : columns) {
            if (column.getColumnName().equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        } else {
            return target;
        }
    }

    @Override
    public void setProperties(Properties properties) {
        if (null != properties && !properties.isEmpty()) {
            props = properties;
        }
    }

    public static void ignoreDataThisTime() {
        IGNORE_DATA.set(System.currentTimeMillis());
    }

}