// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.common.dto.*;
import com.cs.persistence.entity.AccessRecordEntity;
import org.apache.ibatis.annotations.*;

import java.util.*;

public interface AccessRecordMapper extends BaseMapper<AccessRecordEntity> {

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT "
            + "  (SELECT count(*) from DATAPOLY_API_ASSIGNMENT) as `totalCount`, "
            + "  (SELECT count(*) from DATAPOLY_API_ASSIGNMENT where `open`=1) as `openCount`, "
            + "  (SELECT count(*) from DATAPOLY_API_ONLINE) as `publishCount`, "
            + "  (SELECT count(*) from DATAPOLY_DATASOURCE) as `datasourceCount` "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT "
            + "  (SELECT count(*) from DATAPOLY_API_ASSIGNMENT) as \"totalCount\", "
            + "  (SELECT count(*) from DATAPOLY_API_ASSIGNMENT where open=true) as \"openCount\", "
            + "  (SELECT count(*) from DATAPOLY_API_ONLINE) as \"publishCount\", "
            + "  (SELECT count(*) from DATAPOLY_DATASOURCE) as \"datasourceCount\" "
            + "</if>"
            + "</script>")
    Map<String, Integer> selectCount();

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT path as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + "GROUP BY path "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT path as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY path "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "</script>")
    List<NameCount> getTopPathAccess(@Param("days") Integer days, @Param("limit") Integer limit);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT ip_addr as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + "GROUP BY ip_addr "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT ip_addr as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY ip_addr "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "</script>")
    List<NameCount> getTopIpAddrAccess(@Param("days") Integer days, @Param("limit") Integer limit);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT "
            + "  IFNULL((select name from DATAPOLY_APP_CLIENT t where app_key = client_key),'空') as name, "
            + "  count(1) as count "
            + "from DATAPOLY_ACCESS_RECORD  "
            + "WHERE DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) &lt;= date(create_time) "
            + "GROUP BY client_key "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT "
            + "  COALESCE((select name from DATAPOLY_APP_CLIENT t where app_key = client_key),'空') as name, "
            + "  count(1) as count "
            + "from DATAPOLY_ACCESS_RECORD  "
            + "WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY client_key "
            + "ORDER BY count DESC "
            + "LIMIT ${limit} "
            + "</if>"
            + "</script>")
    List<NameCount> getTopAppClientAccess(@Param("days") Integer days, @Param("limit") Integer limit);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT concat('HTTP(',status,')') as name,count(1) as count from DATAPOLY_ACCESS_RECORD  "
            + "WHERE DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + "GROUP BY status "
            + "ORDER BY count DESC "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT concat('HTTP(',status,')') as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY status "
            + "ORDER BY count DESC "
            + "</if>"
            + "</script>")
    List<NameCount> getHttpStatusCount(@Param("days") Integer days);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT "
            + " DATE_FORMAT(create_time,'%Y-%m-%d') as of_date , "
            + " count(*) as total, "
            + " sum(success) as success "
            + "FROM (  "
            + "  SELECT id,path,case when status=200 then 1 else 0 end as success, create_time "
            + "  FROM DATAPOLY_ACCESS_RECORD "
            + "  WHERE DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + " ) t  "
            + " GROUP BY of_date"
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT "
            + " to_char(create_time, 'YYYY-MM-DD') as of_date , "
            + " count(*) as total, "
            + " sum(success) as success "
            + "FROM (  "
            + "  SELECT id,path,case when status=200 then 1 else 0 end as success, create_time "
            + "  FROM DATAPOLY_ACCESS_RECORD "
            + "  WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + " ) t  "
            + " GROUP BY of_date"
            + "</if>"
            + "</script>")
    List<DateCount> getDailyTrend(@Param("days") Integer days);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT concat('HTTP(',status,')') as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + "GROUP BY status "
            + "ORDER BY count DESC "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT concat('HTTP(',status,')') as name,count(1) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY status "
            + "ORDER BY count DESC "
            + "</if>"
            + "</script>")
    List<NameCount> getApiHttpStatusCount(@Param("apiId") Long apiId, @Param("days") Integer days);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT "
            + " DATE_FORMAT(create_time,'%Y-%m-%d') as of_date , "
            + " count(*) as total, "
            + " sum(case when status=200 then 1 else 0 end) as success "
            + "FROM DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) <![CDATA[ <=  ]]> date(create_time) "
            + "GROUP BY of_date "
            + "ORDER BY of_date ASC "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT "
            + " to_char(create_time, 'YYYY-MM-DD') as of_date , "
            + " count(*) as total, "
            + " sum(case when status=200 then 1 else 0 end) as success "
            + "FROM DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "GROUP BY of_date "
            + "ORDER BY of_date ASC "
            + "</if>"
            + "</script>")
    List<DateCount> getApiDailyTrend(@Param("apiId") Long apiId, @Param("days") Integer days);

    @Select("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "SELECT HOUR(create_time) as hour,count(*) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND TO_DAYS(create_time) = TO_DAYS(#{date}) "
            + "GROUP BY HOUR(create_time) "
            + "ORDER BY hour ASC "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "SELECT EXTRACT(HOUR FROM create_time)::int as hour,count(*) as count from DATAPOLY_ACCESS_RECORD "
            + "WHERE api_id = #{apiId} "
            + "AND create_time::date = #{date}::date "
            + "GROUP BY EXTRACT(HOUR FROM create_time) "
            + "ORDER BY hour ASC "
            + "</if>"
            + "</script>")
    List<HourCount> getApiHourlyTrend(@Param("apiId") Long apiId, @Param("date") String date);

    @Delete("<script>"
            + "<if test='_databaseId == \"mysql\" '>"
            + "DELETE FROM DATAPOLY_ACCESS_RECORD "
            + "WHERE date(create_time) &lt;= DATE_SUB( CURDATE(), INTERVAL ${days} DAY ) "
            + "</if>"
            + "<if test='_databaseId == \"postgresql\" '>"
            + "DELETE FROM DATAPOLY_ACCESS_RECORD "
            + "WHERE CURRENT_DATE - INTERVAL'${days} day' <![CDATA[ <=  ]]> create_time::date "
            + "</if>"
            + "</script>")
    void deleteHistoryBeforeDays(@Param("days") Integer days);
}
