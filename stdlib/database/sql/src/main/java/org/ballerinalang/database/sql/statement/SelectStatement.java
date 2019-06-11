/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.database.sql.statement;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.CallableUnitCallback;
import org.ballerinalang.database.sql.Constants;
import org.ballerinalang.database.sql.SQLDatasource;
import org.ballerinalang.database.sql.SQLDatasourceUtils;
import org.ballerinalang.model.ColumnDefinition;
import org.ballerinalang.model.types.BStructureType;
import org.ballerinalang.model.values.BError;
import org.ballerinalang.model.values.BValueArray;
import org.ballerinalang.util.TableResourceManager;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
/**
 * Represents a Select SQL statement.
 *
 * @since 1.0.0
 */
public class SelectStatement extends AbstractSqlStatement {

    private final Context context;
    private final SQLDatasource datasource;
    private final String query;
    private final BValueArray parameters;
    private final BStructureType structType;
    private final boolean loadSQLTableToMemory;
    private final CallableUnitCallback callback;

    public SelectStatement(Context context, SQLDatasource datasource, String query, BValueArray parameters,
                           BStructureType structType, boolean loadSQLTableToMemory, CallableUnitCallback callback) {
        this.context = context;
        this.datasource = datasource;
        this.query = query;
        this.parameters = parameters;
        this.structType = structType;
        this.loadSQLTableToMemory = loadSQLTableToMemory;
        this.callback = callback;
    }

    @Override
    public void execute() {
        checkAndObserveSQLAction(context, datasource, query);
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            BValueArray generatedParams = constructParameters(context, parameters);
            conn = SQLDatasourceUtils.getDatabaseConnection(context, datasource, true);
            String processedQuery = createProcessedQueryString(query, generatedParams);
            stmt = getPreparedStatement(conn, datasource, processedQuery, loadSQLTableToMemory);
            createProcessedStatement(conn, stmt, generatedParams, datasource.getDatabaseProductName());
            rs = stmt.executeQuery();
            TableResourceManager rm = new TableResourceManager(conn, stmt, true);
            List<ColumnDefinition> columnDefinitions = SQLDatasourceUtils.getColumnDefinitions(rs);
            if (loadSQLTableToMemory) {
                CachedRowSet cachedRowSet = RowSetProvider.newFactory().createCachedRowSet();
                cachedRowSet.populate(rs);
                rs = cachedRowSet;
                rm.gracefullyReleaseResources();
            } else {
                rm.addResultSet(rs);
            }
            context.setReturnValues(constructTable(rm, context, rs, structType, columnDefinitions,
                    datasource.getDatabaseProductName()));
            callback.notifySuccess();
        } catch (SQLException | BallerinaException e) {
            SQLDatasourceUtils.cleanupResources(rs, stmt, conn, true);
            BError error = SQLDatasourceUtils.getSQLConnectorError(context, e, "execute query failed: ");
            context.setReturnValues(error);
            callback.notifySuccess();
            SQLDatasourceUtils.handleErrorOnTransaction(context);
            checkAndObserveSQLError(context, "execute query failed: " + e.getMessage());
        } catch (Throwable e) {
            String errorMessage = "execute query failed";
            callback.notifyFailure(SQLDatasourceUtils.getSQLConnectorError(context, e, errorMessage + ":"));
            throw e;
        }
    }

    private PreparedStatement getPreparedStatement(Connection conn, SQLDatasource datasource, String query,
                                                     boolean loadToMemory) throws SQLException {
        PreparedStatement stmt;
        boolean mysql = datasource.getDatabaseProductName().contains(Constants.DatabaseNames.MYSQL);
        /* In MySQL by default, ResultSets are completely retrieved and stored in memory.
           Following properties are set to stream the results back one row at a time.*/
        if (mysql && !loadToMemory) {
            stmt = conn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            // To fulfill OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE findbugs validation.
            try {
                stmt.setFetchSize(Integer.MIN_VALUE);
            } catch (SQLException e) {
                stmt.close();
            }
        } else {
            stmt = conn.prepareStatement(query);
        }
        return stmt;
    }
}
