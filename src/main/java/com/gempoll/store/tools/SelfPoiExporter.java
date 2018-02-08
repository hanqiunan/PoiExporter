package com.gempoll.store.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jfinal.plugin.activerecord.Model;
import com.jfinal.plugin.activerecord.Record;

public class SelfPoiExporter{

    public static final String VERSION_2003 = "2003";
    private static final int HEADER_ROW = 1;
    private static final int MAX_ROWS = 65535;
    private String version;
    private String[] sheetNames = new String[]{"sheet","sheet2"};
    private int cellWidth = 8000;
    private int headerRow;
    private String[][] headers;
    private String[][] columns;
    private List<?>[] data;

    public SelfPoiExporter(List<?>... data) {
        this.data = data;
    }

    public static SelfPoiExporter data(List<?>... data) {
        return new SelfPoiExporter(data);
    }

    public static List<List<?>> dice(List<?> num, int chunkSize) {
        int size = num.size();
        int chunk_num = size / chunkSize + (size % chunkSize == 0 ? 0 : 1);
        List<List<?>> result = Lists.newArrayList();
        for (int i = 0; i < chunk_num; i++) {
            result.add(Lists.newArrayList(num.subList(i * chunkSize, i == chunk_num - 1 ? size : (i + 1) * chunkSize)));
        }
        return result;
    }

    public Workbook export() {
        Preconditions.checkNotNull(data, "data can not be null");
        Preconditions.checkNotNull(headers, "headers can not be null");
        Preconditions.checkNotNull(columns, "columns can not be null");
        Preconditions.checkArgument(data.length == sheetNames.length && sheetNames.length == headers.length
                && headers.length == columns.length, "data,sheetNames,headers and columns'length should be the same." +
                "(data:" + data.length + ",sheetNames:" + sheetNames.length + ",headers:" + headers.length + ",columns:" + columns.length + ")");
        Preconditions.checkArgument(cellWidth >= 0, "cellWidth can not be less than 0");
        Workbook wb;
        if (VERSION_2003.equals(version)) {
            wb = new HSSFWorkbook();
            if (data.length > 1) {
                for (int i = 0; i < data.length; i++) {
                    List<?> item = data[i];
                    Preconditions.checkArgument(item.size() < MAX_ROWS, "Data [" + i + "] is invalid:invalid data size (" + item.size() + ") outside allowable range (0..65535)");
                }
            } else if (data.length == 1 && data[0].size() > MAX_ROWS) {
                data = dice(data[0], MAX_ROWS).toArray(new List<?>[]{});
                String sheetName = sheetNames[0];
                sheetNames = new String[data.length];
                for (int i = 0; i < data.length; i++) {
                    sheetNames[i] = sheetName + (i == 0 ? "" : (i + 1));
                }
                String[] header = headers[0];
                headers = new String[data.length][];
                for (int i = 0; i < data.length; i++) {
                    headers[i] = header;
                }
                String[] column = columns[0];
                columns = new String[data.length][];
                for (int i = 0; i < data.length; i++) {
                    columns[i] = column;
                }
            }
        } else {
            wb = new SXSSFWorkbook();
        }
        if (data.length == 0) {
            return wb;
        }
        for (int i = 0; i < data.length; i++) {
            Sheet sheet = wb.createSheet(sheetNames[i]);
            Row row;
            Cell cell;
            if (headers[i].length > 0) {
                row = sheet.createRow(0);
                if (headerRow <= 0) {
                    headerRow = HEADER_ROW;
                }
                headerRow = Math.min(headerRow, MAX_ROWS);
                for (int h = 0, lenH = headers[i].length; h < lenH; h++) {
                    if (cellWidth > 0) {
                        sheet.setColumnWidth(h, cellWidth);
                    }
                    cell = row.createCell(h);
                    cell.setCellValue(headers[i][h]);
                }
            }

            for (int j = 0, len = data[i].size(); j < len; j++) {
                row = sheet.createRow(j + headerRow);
                Object obj = data[i].get(j);
                if (obj == null) {
                    continue;
                }
                if (obj instanceof Map) {
                    processAsMap(columns[i], row, obj);
                } else if (obj instanceof Model) {
                    processAsModel(columns[i], row, obj);
                } else if (obj instanceof Record) {
                    processAsRecord(columns[i], row, obj);
                } else {
                    throw new RuntimeException("Not support type[" + obj.getClass() + "]");
                }
            }
        }
        return wb;
    }

    @SuppressWarnings("unchecked")
    private static void processAsMap(String[] columns, Row row, Object obj) {
        Cell cell;
        Map<String, Object> map = (Map<String, Object>) obj;
        if (columns.length == 0) { // show all if column not specified
            Set<String> keys = map.keySet();
            int columnIndex = 0;
            for (String key : keys) {
                cell = row.createCell(columnIndex);
                if(map.get(key)!= null){
                	cell.setCellValue(map.get(key) + "");
                }
                //cell.setCellValue(map.get(key)== null ? null : map.get(key) + "");
                columnIndex++;
            }
        } else {
            for (int j = 0, len = columns.length; j < len; j++) {
                cell = row.createCell(j);
                cell.setCellValue(map.get(columns[j]) == null ? null : map.get(columns[j]) + "");
            }
        }
    }

    private static void processAsModel(String[] columns, Row row, Object obj) {
        Cell cell;
        Model<?> model = (Model<?>) obj;
        Set<Entry<String, Object>> entries = model.getAttrsEntrySet();
        if (columns.length == 0) { // show all if column not specified
            int columnIndex = 0;
            for (Entry<String, Object> entry : entries) {
                if(entry.getValue() != null){
                	cell = row.createCell(columnIndex);
                	cell.setCellValue(entry.getValue() + "");
                }
                //cell.setCellValue(entry.getValue() == null ? null : entry.getValue() + "");
                columnIndex++;
            }
        } else {
            for (int j = 0, len = columns.length; j < len; j++) {
				if(model.get(columns[j])!=null){
					cell = row.createCell(j);
					cell.setCellValue(model.get(columns[j]) + "");
				}
                //cell.setCellValue(model.get(columns[j]) == null ? null : model.get(columns[j]) + "");
            }
        }
    }

    private static void processAsRecord(String[] columns, Row row, Object obj) {
        Cell cell;
        Record record = (Record) obj;
        Map<String, Object> map = record.getColumns();
        if (columns.length == 0) { // show all if column not specified
            record.getColumns();
            Set<String> keys = map.keySet();
            int columnIndex = 0;
            for (String key : keys) {
                cell = row.createCell(columnIndex);
                if(record.get(key) != null){
                	cell.setCellValue(record.get(key) + "");
                }
               // cell.setCellValue(record.get(key) == null ? null : record.get(key) + "");
                columnIndex++;
            }
        } else {
            for (int j = 0, len = columns.length; j < len; j++) {
                cell = row.createCell(j);
                cell.setCellValue(map.get(columns[j]) == null ? null : map.get(columns[j]) + "");
            }
        }
    }

    public SelfPoiExporter version(String version) {
        this.version = version;
        return this;
    }

    public SelfPoiExporter sheetName(String sheetName) {
        this.sheetNames = new String[]{sheetName};
        return this;
    }

    public SelfPoiExporter sheetNames(String... sheetName) {
        this.sheetNames = sheetName;
        return this;
    }

    public SelfPoiExporter cellWidth(int cellWidth) {
        this.cellWidth = cellWidth;
        return this;
    }

    public SelfPoiExporter headerRow(int headerRow) {
        this.headerRow = headerRow;
        return this;
    }

    public SelfPoiExporter header(String... header) {
        this.headers = new String[][]{header};
        return this;
    }

    public SelfPoiExporter headers(String[]... headers) {
        this.headers = headers;
        return this;
    }

    public SelfPoiExporter column(String... column) {
        this.columns = new String[][]{column};
        return this;
    }

    public SelfPoiExporter columns(String[]... columns) {
        this.columns = columns;
        return this;
    }

}
