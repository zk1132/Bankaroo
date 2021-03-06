package dtu.dagprojekt.bankaroo.database;

import com.google.gson.stream.JsonWriter;


import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.sql.*;
import java.util.LinkedList;
import java.util.Map;

public class Query {

    private static final int TIMEOUT = 5;

    public static final String DESC = "DESC";

    private StringBuilder sql;
    private int updateCount;
    private ResultSet resultSet;

    private boolean isFirstElement = true;

    private boolean isSelectStatement = false;
    private PreparedStatement statement;

    public LinkedList<Object> getSqlParams() {
        return sqlParams;
    }

    public void debugSqlParams() {
        String seperator = "";
        for (Object obj : sqlParams){
            System.out.print(seperator + obj);
            seperator = ", ";
        }
        System.out.println();
    }

    LinkedList<Object> sqlParams;


    public Query() {
        this.sql = new StringBuilder();
        this.sqlParams = new LinkedList<Object>();
    }

    @Override
    public String toString() {
        return sql.toString();
    }

    public Query call(Procedure transaction) {
        sql.append("CALL ").append("\"").append(DB.TABLE).append("\".");
        sql.append(transaction);
        this.isSelectStatement = false;
        return this;
    }

    public Query params(Object... param) {
        sql.append("(");
        String prepend = "";
        for (Object obj : param){
            sql.append(prepend);
            appendValueSQL(sql, obj);
            prepend = ", ";
        }
        sql.append(")");

        return this;

    }

    private StringBuilder appendValueSQL(StringBuilder sql, Object obj){
        if (obj.equals("DEFAULT")){
            sql.append(obj);
        } else {
            sql.append("?");
            sqlParams.add(obj);
        }
        return sql;
    }

    public Query values(Object... values) {
        sql.append("VALUES(");
        String prepend = "";
        for (Object obj : values){
            sql.append(prepend);
            appendValueSQL(sql, obj);
            prepend = ", ";
        }
        sql.append(") ");
        return this;
    }

    public Query update(Schema schema){
        sql.append("UPDATE \"").append(DB.TABLE).append("\"");
        sql.append(".\"").append(schema.toString()).append("\" ");
        isSelectStatement = false;
        return this;
    }

    public Query insert(Schema schema) {
        sql.append("INSERT INTO \"").append(DB.TABLE).append("\"");
        sql.append(".\"").append(schema.toString()).append("\" ");
        isSelectStatement = false;
        return this;
    }

    public Query delete() {
        sql.append("DELETE ");
        isSelectStatement = false;
        return this;
    }

    public Query select() {
        sql.append("SELECT ");
        isSelectStatement = true;
        return this;
    }

    public Query all() {
        sql.append("* ");
        return this;
    }

    public Query from(Schema schema) {
        sql.append("FROM \"").append(DB.TABLE).append("\"");
        sql.append(".\"").append(schema.toString()).append("\"");
        return this;
    }

    public Query set(Map<Enum, Object> updates){
        for(Map.Entry<Enum, Object> entry : updates.entrySet()){
            set(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public Query set(Enum field, Object value) {
        if (isFirstElement){
            sql.append("SET ");
            isFirstElement = false;
        } else {
            sql.append(", ");
        }

        sql.append("\"").append(field.toString()).append("\"");
        sql.append(" = ");
        appendValueSQL(sql, value);

        return this;
    }

    public Query where(String field){
        sql.append(" WHERE ").append("\"").append(field).append("\" ");
        return this;
    }

    public Query where() {
        sql.append(" WHERE ");
        return this;
    }

    public Query where(Enum field){
        return where(field.toString());
    }

    public Query equal(Object value){
        sql.append("= ").append("?");
        sqlParams.add(value);
        return this;
    }

    public Query upperLike(Enum field, Object value){
        // UPPER("field") LIKE UPPER('%value%')
        sql.append("UPPER(\"").append(field).append("\") ");
        sql.append("LIKE UPPER(?) ");
        sqlParams.add("%"+value+"%");
        return this;
    }

    public Query startsLike(Object value) {
        sql.append("LIKE ? ");
        sqlParams.add(value+"%");
        return this;
    }

    public String getQuery(){
        return sql.toString();
    }

    public Query execute() throws SQLException {
        statement = DB.getConnection().prepareStatement(sql.toString());
        statement.setQueryTimeout(TIMEOUT);

        replaceValues(statement);

        if (isSelectStatement){
            this.resultSet = statement.executeQuery();
        } else {
            this.updateCount = statement.executeUpdate();
        }

        return this;
    }

    private void replaceValues(PreparedStatement statement) throws SQLException {
        int i = 1;
        for (Object value : sqlParams){
            if (value == null){
                statement.setNull(i, Types.VARCHAR);
            }else if (value instanceof String){
                statement.setString(i, String.valueOf(value));
            } else if (value instanceof Integer){
                statement.setInt(i, (Integer) value);
            } else if (value instanceof Double) {
                statement.setDouble(i, (Double) value);
            } else if (value instanceof Long) {
                statement.setLong(i, (Long) value);
            } else {
                statement.setObject(i, value);
            }

            i++;
        }
    }

    public ResultSet resultSet(){
        return resultSet;
    }

    public StreamingOutput toJson() throws SQLException {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream os) {
                try {
                    Writer out = new BufferedWriter(new OutputStreamWriter(os, "UTF8"));
                    writeJson(out);
                    out.flush();
                    close();
                } catch (SQLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void writeJson(Writer out) throws IOException, SQLException {
        JsonWriter writer = new JsonWriter(out);

        writer.beginArray();
        while(resultSet.next()) {
            writer.beginObject();
            ResultSetMetaData metaData = resultSet.getMetaData();
            for(int i = 1; i <= metaData.getColumnCount(); i++) {
                writer.name(metaData.getColumnLabel(i));
                writer.value(resultSet.getString(i));
            }
            writer.endObject();
        }
        writer.endArray();
        writer.flush();
    }

    public Query close() throws SQLException {
        // Close the Statement (also closes result set)
        statement.close();

        // Connection must be on a unit-of-work boundary to allow close
        DB.getConnection().commit();
        return this;
    }


    public Query expect(int i) throws SQLException {
        if (updateCount != i){
            throw new SQLException("Expected " + i + " row(s) to be updated but updated " + updateCount);
        }
        return this;
    }

    public Query orderBy(Enum field, String order) {
        // ORDER BY "field" order
        sql.append("ORDER BY \"").append(field).append("\" ");
        sql.append(order).append(" ");
        return this;
    }

    public Query or() {
        sql.append("OR ");
        return this;
    }

    public Query cast(Enum field, String type, int length) {
        sql.append("CAST(\"").append(field).append("\" AS ");
        sql.append(type);

        if (length >= 0){
            sql.append("(").append(length).append(")");
        }

        sql.append(") ");
        return this;
    }
}
