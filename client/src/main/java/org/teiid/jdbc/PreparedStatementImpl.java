/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.teiid.client.RequestMessage;
import org.teiid.client.RequestMessage.ResultsMode;
import org.teiid.client.RequestMessage.StatementType;
import org.teiid.client.metadata.MetadataResult;
import org.teiid.client.util.ResultsFuture;
import org.teiid.core.TeiidComponentException;
import org.teiid.core.TeiidProcessingException;
import org.teiid.core.types.BlobImpl;
import org.teiid.core.types.ClobImpl;
import org.teiid.core.types.DataTypeManager;
import org.teiid.core.types.InputStreamFactory;
import org.teiid.core.types.JDBCSQLTypeInfo;
import org.teiid.core.types.Streamable;
import org.teiid.core.util.ReaderInputStream;
import org.teiid.core.util.SqlUtil;
import org.teiid.core.util.TimestampWithTimezone;


/**
 * <p> Instances of PreparedStatement contain a SQL statement that has already been
 * compiled.  The SQL statement contained in a PreparedStatement object may have
 * one or more IN parameters. An IN parameter is a parameter whose value is not
 * specified when a SQL statement is created. Instead the statement has a placeholder
 * for each IN parameter.</p>
 * <p> The MMPreparedStatement object wraps the server's PreparedStatement object.
 * The methods in this class are used to set the IN parameters on a server's
 * preparedstatement object.</p>
 */

public class PreparedStatementImpl extends StatementImpl implements TeiidPreparedStatement {
	// sql, which this prepared statement is operating on
    protected String prepareSql;

    //map that holds parameter index to values for prepared statements
    private Map<Integer, Object> parameterMap;
    private TreeMap<String, Integer> paramsByName;
    
    //a list of map that holds parameter index to values for prepared statements
    protected List<List<Object>> batchParameterList;

    // metadata
	private MetadataResult metadataResults;
    private ResultSetMetaData metadata;
    private ParameterMetaDataImpl parameterMetaData;
    
    private Calendar serverCalendar;

    /**
     * Factory Constructor 
     * @param connection
     * @param sql
     * @param resultSetType
     * @param resultSetConcurrency
     */
    static PreparedStatementImpl newInstance(ConnectionImpl connection, String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return new PreparedStatementImpl(connection, sql, resultSetType, resultSetConcurrency);        
    }
    
    /**
     * <p>MMPreparedStatement constructor.
     * @param Driver's connection object.
     * @param String object representing the prepared statement
     */
    PreparedStatementImpl(ConnectionImpl connection, String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        super(connection, resultSetType, resultSetConcurrency);

        if (sql == null) {
        	throw new TeiidSQLException(JDBCPlugin.Util.getString("MMPreparedStatement.Err_prep_sql")); //$NON-NLS-1$
        }
        this.prepareSql = sql;

        TimeZone timezone = connection.getServerConnection().getLogonResult().getTimeZone();

        if (timezone != null && !timezone.hasSameRules(getDefaultCalendar().getTimeZone())) {
        	this.serverCalendar = Calendar.getInstance(timezone);
        }        
    }

    /**
     * <p>Adds a set of parameters to this PreparedStatement object's list of commands
     * to be sent to the database for execution as a batch.
     * @throws SQLException if there is an error
     */
    public void addBatch() throws SQLException {
        checkStatement();
    	if(batchParameterList == null){
    		batchParameterList = new ArrayList<List<Object>>();
		}
    	batchParameterList.add(getParameterValues());
		clearParameters();
    }

    /**
     * Makes the set of commands in the current batch empty.
     *
     * @throws SQLException if a database access error occurs or the
     * driver does not support batch statements
     */
    public void clearBatch() throws SQLException {
    	if (batchParameterList != null ) {
    		batchParameterList.clear();
    	}
    }

    /**
     * <p>Clears the values set for the PreparedStatement object's IN parameters and
     * releases the resources used by those values. In general, parameter values
     * remain in force for repeated use of statement.
     * @throws SQLException if there is an error while clearing params
     */
    public void clearParameters() throws SQLException {
        checkStatement();
        //clear the parameters list on servers prepared statement object
        if(parameterMap != null){
            parameterMap.clear();
        }
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new TeiidSQLException(msg);
    }
    
    @Override
    public void submitExecute(String sql, StatementCallback callback, RequestOptions options) throws TeiidSQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new TeiidSQLException(msg);
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new TeiidSQLException(msg);
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new TeiidSQLException(msg);
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
    	String msg = JDBCPlugin.Util.getString("JDBC.Method_not_supported"); //$NON-NLS-1$
        throw new TeiidSQLException(msg);
    }
    
    @Override
    public void submitExecute(StatementCallback callback, RequestOptions options)
    		throws SQLException {
    	NonBlockingRowProcessor processor = new NonBlockingRowProcessor(this, callback);
    	submitExecute(ResultsMode.EITHER, options).addCompletionListener(processor);
    }
    
    public ResultsFuture<Boolean> submitExecute(ResultsMode mode, RequestOptions options) throws SQLException {
        return executeSql(new String[] {this.prepareSql}, false, mode, false, options);
    }

	@Override
    public boolean execute() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, ResultsMode.EITHER, true, null);
        return hasResultSet();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
    	if (batchParameterList == null || batchParameterList.isEmpty()) {
   	     	return new int[0];
    	}
	   	try{
	   		executeSql(new String[] {this.prepareSql}, true, ResultsMode.UPDATECOUNT, true, null);
	   	}finally{
	   		batchParameterList.clear();
	   	}
	   	return this.updateCounts;
    }

	@Override
    public ResultSet executeQuery() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, ResultsMode.RESULTSET, true, null);
        return resultSet;
    }

	@Override
    public int executeUpdate() throws SQLException {
        executeSql(new String[] {this.prepareSql}, false, ResultsMode.UPDATECOUNT, true, null);
        return this.updateCounts[0];
    }
    
    @Override
    protected RequestMessage createRequestMessage(String[] commands,
    		boolean isBatchedCommand, ResultsMode resultsMode) {
    	RequestMessage message = super.createRequestMessage(commands, false, resultsMode);
    	message.setStatementType(StatementType.PREPARED);
    	message.setParameterValues(isBatchedCommand?getParameterValuesList(): getParameterValues());
    	message.setBatchedUpdate(isBatchedCommand);
    	return message;
    }

    public ResultSetMetaData getMetaData() throws SQLException {

        // check if the statement is open
        checkStatement();

        if(metadata == null) {
        	if (updateCounts != null) {
        		return null;
        	} else if(resultSet != null) {
                metadata = resultSet.getMetaData();
            } else {
				if (getMetadataResults().getColumnMetadata() == null) {
					return null;
				}
                MetadataProvider provider = new MetadataProvider(getMetadataResults().getColumnMetadata());
                metadata = new ResultSetMetaDataImpl(provider, this.getExecutionProperty(ExecutionProperties.JDBC4COLUMNNAMEANDLABELSEMANTICS));
            }
        }

        return metadata;
    }

	private MetadataResult getMetadataResults() throws TeiidSQLException {
		if (metadataResults == null) {
			try {
				metadataResults = this.getDQP().getMetadata(this.currentRequestID, prepareSql, Boolean.valueOf(getExecutionProperty(ExecutionProperties.ANSI_QUOTED_IDENTIFIERS)).booleanValue());
			} catch (TeiidComponentException e) {
				throw TeiidSQLException.create(e);
			} catch (TeiidProcessingException e) {
				throw TeiidSQLException.create(e);
			}
		}
		return metadataResults;
	}

    public void setAsciiStream(int parameterIndex, java.io.InputStream in, int length) throws SQLException {
    	setAsciiStream(parameterIndex, in);
    }

    public void setBigDecimal (int parameterIndex, java.math.BigDecimal value) throws SQLException {
        setObject(parameterIndex, value);
    }

    public void setBinaryStream(int parameterIndex, java.io.InputStream in, int length) throws SQLException {
    	setBlob(parameterIndex, in);
    }

    public void setBlob (int parameterIndex, Blob x) throws SQLException {
        setObject(parameterIndex, x);
    }

    public void setBoolean (int parameterIndex, boolean value) throws SQLException {
        setObject(parameterIndex, Boolean.valueOf(value));
    }

    public void setByte(int parameterIndex, byte value) throws SQLException {
        setObject(parameterIndex, Byte.valueOf(value));
    }

    public void setBytes(int parameterIndex, byte bytes[]) throws SQLException {
    	setObject(parameterIndex, bytes);
    }

    public void setCharacterStream (int parameterIndex, java.io.Reader reader, int length) throws SQLException {
    	setCharacterStream(parameterIndex, reader);
    }

    public void setClob (int parameterIndex, Clob x) throws SQLException {
        setObject(parameterIndex, x);
    }

    public void setDate(int parameterIndex, java.sql.Date value) throws SQLException {
        setDate(parameterIndex, value, null);
    }
    
    public void setDate(int parameterIndex, java.sql.Date x ,java.util.Calendar cal) throws SQLException {
    	setDate(Integer.valueOf(parameterIndex), x, cal);
    }

    void setDate(Object parameterIndex, java.sql.Date x ,java.util.Calendar cal) throws SQLException {

        if (cal == null || x == null) {
            setObject(parameterIndex, x);
            return;
        }
                
        // set the parameter on the stored procedure
        setObject(parameterIndex, TimestampWithTimezone.createDate(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    public void setDouble(int parameterIndex, double value) throws SQLException {
        setObject(parameterIndex, new Double(value));
    }

    public void setFloat(int parameterIndex, float value) throws SQLException {
        setObject(parameterIndex, new Float(value));
    }

    public void setInt(int parameterIndex, int value) throws SQLException {
        setObject(parameterIndex, Integer.valueOf(value));
    }

    public void setLong(int parameterIndex, long value) throws SQLException {
        setObject(parameterIndex, Long.valueOf(value));
    }

    public void setNull(int parameterIndex, int jdbcType) throws SQLException {
        setObject(parameterIndex, null);
    }

    public void setNull(int parameterIndex, int jdbcType, String typeName) throws SQLException {
        setObject(parameterIndex, null);
    }
    
    public void setObject (int parameterIndex, Object value, int targetJdbcType, int scale) throws SQLException {
    	setObject(parameterIndex, value, targetJdbcType, scale);
    }

    void setObject (Object parameterIndex, Object value, int targetJdbcType, int scale) throws SQLException {

       if(value == null) {
            setObject(parameterIndex, null);
            return;
        }

       if(targetJdbcType != Types.DECIMAL || targetJdbcType != Types.NUMERIC) {
            setObject(parameterIndex, value, targetJdbcType);
        // Decimal and NUMERIC types correspond to java.math.BigDecimal
        } else {
            // transform the object to a BigDecimal
            BigDecimal bigDecimalObject = DataTypeTransformer.getBigDecimal(value);
            // set scale on the BigDecimal
            setObject(parameterIndex, bigDecimalObject.setScale(scale));
        }
    }
    
    public void setObject(int parameterIndex, Object value, int targetJdbcType) throws SQLException {
    	setObject(Integer.valueOf(parameterIndex), value, targetJdbcType);
    }

    void setObject(Object parameterIndex, Object value, int targetJdbcType) throws SQLException {
        Object targetObject = value;

        if(value == null) {
            setObject(parameterIndex, null);
            return;
        }

        // get the java class name for the given JDBC type
        String typeName = JDBCSQLTypeInfo.getTypeName(targetJdbcType);
        int typeCode = DataTypeManager.getTypeCode(DataTypeManager.getDataTypeClass(typeName));
        // transform the value to the target datatype
        switch (typeCode) {
        case DataTypeManager.DefaultTypeCodes.STRING:
        	targetObject = DataTypeTransformer.getString(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.CHAR:
        	targetObject = DataTypeTransformer.getCharacter(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.INTEGER:
        	targetObject = DataTypeTransformer.getInteger(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.BYTE:
        	targetObject = DataTypeTransformer.getByte(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.SHORT:
        	targetObject = DataTypeTransformer.getShort(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.LONG:
        	targetObject = DataTypeTransformer.getLong(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.FLOAT:
        	targetObject = DataTypeTransformer.getFloat(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.DOUBLE:
        	targetObject = DataTypeTransformer.getDouble(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.BOOLEAN:
        	targetObject = DataTypeTransformer.getBoolean(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.BIGDECIMAL:
        	targetObject = DataTypeTransformer.getBigDecimal(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.TIMESTAMP:
        	targetObject = DataTypeTransformer.getTimestamp(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.DATE:
        	targetObject = DataTypeTransformer.getDate(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.TIME:
        	targetObject = DataTypeTransformer.getTime(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.BLOB:
        	targetObject = DataTypeTransformer.getBlob(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.CLOB:
        	targetObject = DataTypeTransformer.getClob(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.XML:
        	targetObject = DataTypeTransformer.getSQLXML(value);
        	break;
        case DataTypeManager.DefaultTypeCodes.VARBINARY:
        	targetObject = DataTypeTransformer.getBytes(value);
        	break;
        }

        setObject(parameterIndex, targetObject);
    }
    
    public void setObject(int parameterIndex, Object value) throws SQLException {
    	setObject(Integer.valueOf(parameterIndex), value);
    }

    void setObject(Object parameterIndex, Object value) throws SQLException {
    	if (parameterIndex instanceof String) {
	    	String s = (String)parameterIndex;
			if (paramsByName == null) {
				paramsByName = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
				ParameterMetaDataImpl pmdi = getParameterMetaData();
				for (int i = 1; i <= pmdi.getParameterCount(); i++) {
					String name = pmdi.getParameterName(i);
					paramsByName.put(name, i);
				}
			}
			parameterIndex = paramsByName.get(s);
			if (parameterIndex == null) {
				throw new TeiidSQLException(JDBCPlugin.Util.getString("MMCallableStatement.Param_not_found", s)); //$NON-NLS-1$
			}
    	}
		if ((Integer)parameterIndex < 1) {
			throw new TeiidSQLException(JDBCPlugin.Util.getString("MMPreparedStatement.Invalid_param_index")); //$NON-NLS-1$
		}

        if(parameterMap == null){
            parameterMap = new TreeMap<Integer, Object>();
        }
        
        if (serverCalendar != null && value instanceof java.util.Date) {
            value = TimestampWithTimezone.create((java.util.Date)value, getDefaultCalendar().getTimeZone(), serverCalendar, value.getClass());
        }
        parameterMap.put((Integer)parameterIndex, value);
    }

    public void setShort(int parameterIndex, short value) throws SQLException {
        setObject(parameterIndex, Short.valueOf(value));
    }

    public void setString(int parameterIndex, String value) throws SQLException {
        setObject(parameterIndex, value);
    }

    public void setTime(int parameterIndex, java.sql.Time value) throws SQLException {
        setTime(parameterIndex, value, null);
    }
    
    public void setTime(int parameterIndex, java.sql.Time x, java.util.Calendar cal) throws SQLException {
    	setTime(Integer.valueOf(parameterIndex), x, cal);
    }

    void setTime(Object parameterIndex, java.sql.Time x, java.util.Calendar cal) throws SQLException {

       if (cal == null || x == null) {
           setObject(parameterIndex, x);
           return;
       }
               
       // set the parameter on the stored procedure
       setObject(parameterIndex, TimestampWithTimezone.createTime(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    public void setTimestamp(int parameterIndex, java.sql.Timestamp value) throws SQLException {
        setTimestamp(parameterIndex, value, null);
    }
    
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) throws SQLException {
    	setTimestamp(Integer.valueOf(parameterIndex), x, cal);
    }

    void setTimestamp(Object parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) throws SQLException {

        if (cal == null || x == null) {
            setObject(parameterIndex, x);
            return;
        }
                
        // set the parameter on the stored procedure
        setObject(parameterIndex, TimestampWithTimezone.createTimestamp(x, cal.getTimeZone(), getDefaultCalendar()));
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        setObject(parameterIndex, x);
    }

    List<List<Object>> getParameterValuesList() {
    	if(batchParameterList == null || batchParameterList.isEmpty()){
    		return Collections.emptyList();
    	}
    	return new ArrayList<List<Object>>(batchParameterList);
    }
    
    List<Object> getParameterValues() {
        if(parameterMap == null || parameterMap.isEmpty()){
            return Collections.emptyList();
        }
        return new ArrayList<Object>(parameterMap.values());
    }

	public ParameterMetaDataImpl getParameterMetaData() throws SQLException {
		if (parameterMetaData == null) {
			//TODO: some of the base implementation of ResultSetMetadata could be on the MetadataProvider
			this.parameterMetaData = new ParameterMetaDataImpl(new ResultSetMetaDataImpl(new MetadataProvider(getMetadataResults().getParameterMetadata()), this.getExecutionProperty(ExecutionProperties.JDBC4COLUMNNAMEANDLABELSEMANTICS)));
		}
		return parameterMetaData;
	}

    /**
     * Exposed for unit testing 
     */
    void setServerCalendar(Calendar serverCalendar) {
        this.serverCalendar = serverCalendar;
    }

	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		setObject(parameterIndex, xmlObject);
	}

	public void setArray(int parameterIndex, Array x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	@Override
	public void setAsciiStream(int parameterIndex, final InputStream x) 
		throws SQLException {
		setAsciiStream(Integer.valueOf(parameterIndex), x);
	}

	void setAsciiStream(Object parameterIndex, final InputStream x)
			throws SQLException {
		if (x == null) {
			this.setObject(parameterIndex, null);
			return;
		}
		this.setObject(parameterIndex, new ClobImpl(new InputStreamFactory() { 
			@Override
			public InputStream getInputStream() throws IOException {
				return x;
			}
		}, -1));
	}

	public void setAsciiStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		setAsciiStream(parameterIndex, x);
	}

	public void setBinaryStream(int parameterIndex, InputStream x)
			throws SQLException {
		setBlob(parameterIndex, x);
	}

	public void setBinaryStream(int parameterIndex, InputStream x, long length)
			throws SQLException {
		setBinaryStream(parameterIndex, x);
	}

	public void setBlob(int parameterIndex, final InputStream inputStream)
	throws SQLException {
		setBlob(parameterIndex, inputStream);
	}
	
	void setBlob(Object parameterIndex, final InputStream inputStream)
			throws SQLException {
		if (inputStream == null) {
			this.setObject(parameterIndex, null);
			return;
		}
		this.setObject(parameterIndex, new BlobImpl(new InputStreamFactory() {
			@Override
			public InputStream getInputStream() throws IOException {
				return inputStream;
			}
		}));
	}

	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		setBlob(parameterIndex, inputStream);
	}

	public void setCharacterStream(int parameterIndex, Reader reader)
			throws SQLException {
		setClob(parameterIndex, reader);
	}

	public void setCharacterStream(int parameterIndex, Reader reader,
			long length) throws SQLException {
		setCharacterStream(parameterIndex, reader);
	}
	
	public void setClob(int parameterIndex, final Reader reader) throws SQLException {
		setClob(Integer.valueOf(parameterIndex), reader);
	}

	void setClob(Object parameterIndex, final Reader reader) throws SQLException {
		if (reader == null) {
			this.setObject(parameterIndex, null);
			return;
		}
		this.setObject(parameterIndex, new ClobImpl(new InputStreamFactory() {
			
			@Override
			public InputStream getInputStream() throws IOException {
				return new ReaderInputStream(reader, Charset.forName(Streamable.ENCODING));
			}
		}, -1));
	}

	public void setClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		setClob(parameterIndex, reader);
	}

	public void setNCharacterStream(int parameterIndex, Reader value)
			throws SQLException {
		setClob(parameterIndex, value);
	}

	public void setNCharacterStream(int parameterIndex, Reader value,
			long length) throws SQLException {
		setCharacterStream(parameterIndex, value);
	}

	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		setObject(parameterIndex, value);
	}

	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		setClob(parameterIndex, reader);
	}

	public void setNClob(int parameterIndex, Reader reader, long length)
			throws SQLException {
		setClob(parameterIndex, reader);
	}

	public void setNString(int parameterIndex, String value)
			throws SQLException {
		setObject(parameterIndex, value);
	}

	public void setRef(int parameterIndex, Ref x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}

	public void setUnicodeStream(int parameterIndex, InputStream x, int length)
			throws SQLException {
		throw SqlUtil.createFeatureNotSupportedException();
	}
}
