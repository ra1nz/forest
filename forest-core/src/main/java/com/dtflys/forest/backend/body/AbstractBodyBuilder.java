package com.dtflys.forest.backend.body;

import com.dtflys.forest.backend.BodyBuilder;
import com.dtflys.forest.converter.json.ForestJsonConverter;
import com.dtflys.forest.handler.LifeCycleHandler;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.http.ForestRequestBody;
import com.dtflys.forest.http.NameValueRequestBody;
import com.dtflys.forest.http.ObjectRequestBody;
import com.dtflys.forest.http.StringRequestBody;
import com.dtflys.forest.mapping.MappingTemplate;
import com.dtflys.forest.multipart.ForestMultipart;
import com.dtflys.forest.utils.ReflectUtils;
import com.dtflys.forest.utils.RequestNameValue;
import com.dtflys.forest.utils.StringUtils;
import com.google.common.collect.Lists;

import java.lang.reflect.Array;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * 通用的请求体构造器抽象类
 * @author gongjun[jun.gong@thebeastshop.com]
 * @since 2018-02-27 18:06
 */
public abstract class AbstractBodyBuilder<T> implements BodyBuilder<T> {

    public final static String TYPE_APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    public final static String TYPE_APPLICATION_JSON = "application/json";
    public final static String TYPE_MULTIPART_FORM_DATA = "multipart/form-data";

    @Override
    public void buildBody(T httpRequest, ForestRequest request, LifeCycleHandler lifeCycleHandler) {
        String contentType = request.getContentType();
        if (StringUtils.isEmpty(contentType)) {
            String value = request.getHeaders().getValue("Content-Type");
            if (value != null) {
                if (value.length() > 0) {
                    contentType = value;
                }
            }
        }

        if (StringUtils.isEmpty(contentType)) {
            contentType = TYPE_APPLICATION_X_WWW_FORM_URLENCODED;
        }

        String[] typeGroup = contentType.split(";[ ]*charset=");
        String mineType = typeGroup[0];
        String charset = request.getCharset();
        boolean mergeCharset = false;
        if (StringUtils.isEmpty(charset)) {
            if (typeGroup.length > 1) {
                charset = typeGroup[1];
                mergeCharset = true;
            } else {
                charset = "UTF-8";
            }
        }

        if (StringUtils.isEmpty(mineType)) {
            mineType = TYPE_APPLICATION_X_WWW_FORM_URLENCODED;
        }
        List<RequestNameValue> nameValueList = request.getDataNameValueList();

        if (mineType.equals(TYPE_APPLICATION_X_WWW_FORM_URLENCODED) && !nameValueList.isEmpty()) {
            setFormBody(httpRequest, request, charset, contentType, nameValueList);
        }
        else if (mineType.equals(TYPE_APPLICATION_JSON)) {
            ForestJsonConverter jsonConverter = request.getConfiguration().getJsonConverter();
            List<ForestRequestBody> srcBodyList = request.getBody();
            List<ForestRequestBody> bodyList = new LinkedList(srcBodyList);
//            Map<String, Object> map = convertNameValueListToMap(request, nameValueList);
//            if (map != null && !map.isEmpty()) {
//                bodyList.add(map);
//            }
            if (!bodyList.isEmpty()) {
                Object toJsonObj = bodyList;
                if (bodyList.size() == 1) {
                    toJsonObj = bodyList.get(0);
                } else {
                    Map<String, Object> jsonMap = null;
                    List jsonArray = null;
                    for (ForestRequestBody bodyItem : bodyList) {
                        if (bodyItem instanceof NameValueRequestBody) {
                            if (jsonMap == null) {
                                jsonMap = new LinkedHashMap<>(bodyList.size());
                            }
                            jsonMap.put(((NameValueRequestBody) bodyItem).getName(), ((NameValueRequestBody) bodyItem).getValue());
                        } else if (bodyItem instanceof StringRequestBody) {
                            String content = bodyItem.toString();
                            Map subMap = null;
                            try {
                                subMap = jsonConverter.convertObjectToMap(content);
                            } catch (Throwable th) {}
                            if (subMap != null) {
                                jsonMap.putAll(subMap);
                            } else {
                                if (jsonArray == null) {
                                    jsonArray = new LinkedList<>();
                                }
                                jsonArray.add(content);
                            }
                        } else if (bodyItem instanceof ObjectRequestBody) {
                            Object obj = ((ObjectRequestBody) bodyItem).getObject();
                            if (obj == null) {
                                continue;
                            }
                            if (obj instanceof List) {
                                if (jsonArray == null) {
                                    jsonArray = new LinkedList();
                                }
                                jsonArray.addAll((List) obj);
                            } else {
                                Map subMap = null;
                                try {
                                    subMap = jsonConverter.convertObjectToMap(obj);
                                } catch (Throwable th) {}
                                if (subMap == null) {
                                    continue;
                                }
                                jsonMap.putAll(subMap);
                            }
                        }
                    }
                    if (jsonMap != null) {
                        toJsonObj = jsonMap;
                    } else if (jsonArray != null) {
                        toJsonObj = jsonArray;
                    }
                }
                String text = null;
                if (toJsonObj instanceof CharSequence || toJsonObj instanceof StringRequestBody) {
                    text = toJsonObj.toString();
                } else if (toJsonObj instanceof ObjectRequestBody) {
                    text = jsonConverter.encodeToString(((ObjectRequestBody) toJsonObj).getObject());
                } else if (toJsonObj instanceof NameValueRequestBody) {
                    Map<String, Object> subMap = new HashMap<>(1);
                    subMap.put(((NameValueRequestBody) toJsonObj).getName(), ((NameValueRequestBody) toJsonObj).getValue());
                    text = jsonConverter.encodeToString(subMap);
                } else {
                    text = jsonConverter.encodeToString(toJsonObj);
                }
                setStringBody(httpRequest, text, charset, contentType, mergeCharset);
            } else {
                setStringBody(httpRequest, "", charset, contentType, mergeCharset);
            }
        }
        else if (mineType.startsWith("multipart/")) {
            List<ForestMultipart> multiparts = request.getMultiparts();
            setFileBody(httpRequest, request, charset, contentType, nameValueList, multiparts, lifeCycleHandler);
        }
        else  {
//            Map<String, Object> map = convertNameValueListToMap(request, nameValueList);
//            StringBuilder builder = new StringBuilder();
//            for (Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
//                Map.Entry<String, Object> entry = iterator.next();
//                Object value = entry.getValue();
//                builder.append(value);
//            }
            StringBuilder builder = new StringBuilder();
            List bodyList = request.getBody();
            for (Object bodyItem : bodyList) {
                builder.append(bodyItem.toString());
            }
            setStringBody(httpRequest, builder.toString(), charset, contentType, mergeCharset);
        }
    }

    protected void prepareFormCollectionItem(List<RequestNameValue> newNameValueList, ForestJsonConverter jsonConverter, String name, Collection collection, int target) {
        int index = 0;
        for (Iterator iterator = collection.iterator(); iterator.hasNext(); ) {
            Object item = iterator.next();
            String subName = name + "[" + index + "]";
            prepareFormItem(newNameValueList, jsonConverter, subName, item, target);
            index++;
        }
    }


    protected void prepareFormArrayItem(List<RequestNameValue> newNameValueList, ForestJsonConverter jsonConverter, String name, Object array, int target) {
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            Object item = Array.get(array, i);
            String subName = name + "[" + i + "]";
            prepareFormItem(newNameValueList, jsonConverter, subName, item, target);
        }
    }

    protected void prepareFormMapItem(List<RequestNameValue> newNameValueList, ForestJsonConverter jsonConverter, String name, Map map, int target) {
        for (Iterator<Map.Entry> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry entry = iterator.next();
            Object mapKey = entry.getKey();
            Object mapValue = entry.getValue();
            String subName = name + "." + mapKey;
            prepareFormItem(newNameValueList, jsonConverter, subName, mapValue, target);
        }
    }


    protected void prepareFormItem(List<RequestNameValue> newNameValueList, ForestJsonConverter jsonConverter, String name, Object value, int target) {
        if (StringUtils.isEmpty(name) && value == null) {
            return;
        }
        Class itemClass = value.getClass();
        if (value != null) {
            boolean needCollapse = false;
            if (value instanceof Collection) {
                Collection collection = (Collection) value;
                if (collection.size() <= 8) {
                    for (Iterator iterator = collection.iterator(); iterator.hasNext(); ) {
                        Object item = iterator.next();
                        if (!ReflectUtils.isPrimaryType(item.getClass())) {
                            needCollapse = true;
                            break;
                        }
                    }
                }
            } else if (itemClass.isArray() && !ReflectUtils.isPrimaryArrayType(itemClass)) {
                needCollapse = true;
            }
            if (needCollapse) {
                if (value instanceof Collection) {
                    prepareFormCollectionItem(newNameValueList, jsonConverter, name, (Collection) value, target);
                } else if (itemClass.isArray()) {
                    prepareFormArrayItem(newNameValueList, jsonConverter, name, value, target);
                }
            } else if (ReflectUtils.isPrimaryType(itemClass)
                    || ReflectUtils.isPrimaryArrayType(itemClass)
                    || value instanceof Collection) {
                newNameValueList.add(new RequestNameValue(name, value, target));
            } else if (value instanceof Map) {
                prepareFormMapItem(newNameValueList, jsonConverter, name, (Map) value, target);
            } else {
                Map<String, Object> itemAttrs = jsonConverter.convertObjectToMap(value);
                for (Map.Entry<String, Object> entry : itemAttrs.entrySet()) {
                    String subAttrName = entry.getKey();
                    Object subAttrValue = entry.getValue();
                    String subName = name + "." + subAttrName;
                    prepareFormItem(newNameValueList, jsonConverter, subName, subAttrValue, target);
                }
            }
        }
    }

    protected List<RequestNameValue> prepareFromNameValueList(ForestJsonConverter jsonConverter, List<RequestNameValue> nameValueList) {
        List<RequestNameValue> newNameValueList = new LinkedList<>();
        for (RequestNameValue nameValue : nameValueList) {
            String name = nameValue.getName();
            Object value = nameValue.getValue();
            int target = nameValue.getTarget();
            prepareFormItem(newNameValueList, jsonConverter, name, value, target);
        }
        return newNameValueList;
    }

    /**
     * 设置字符串请求体
     * @param httpReq 后端请求对象
     * @param text 字符串文本
     * @param charset 字符集
     * @param contentType 数据类型
     * @param mergeCharset 是否合并字符集
     */
    protected abstract void setStringBody(T httpReq, String text, String charset, String contentType, boolean mergeCharset);

    /**
     * 设置表单请求体
     * @param httpReq 后端请求对象
     * @param request Forest请求对象
     * @param charset 字符集
     * @param contentType 数据类型
     * @param nameValueList 键值对列表
     */
    protected abstract void setFormBody(T httpReq, ForestRequest request, String charset, String contentType, List<RequestNameValue> nameValueList);

    /**
     * 设置文件请求体
     * @param httpReq 后端请求对象
     * @param request Forest请求对象
     * @param charset 字符集
     * @param contentType 数据类型
     * @param nameValueList 键值对列表
     * @param multiparts Multiparts
     * @param lifeCycleHandler 生命周期处理器
     */
    protected abstract void setFileBody(T httpReq, ForestRequest request, String charset, String contentType, List<RequestNameValue> nameValueList,  List<ForestMultipart> multiparts, LifeCycleHandler lifeCycleHandler);

}
