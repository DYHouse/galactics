package com.dyhouse.galactics.framework.utils;

import com.alibaba.fastjson.JSONObject;
import com.dyhouse.galactics.framework.constants.UtilConstants;
import com.dyhouse.galactics.framework.entity.IPLocation;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by aaron.pan on 2017/3/23.
 */
public class IPAnalyzeUtils {
    private static Cache<String, IPLocation> _cacheFormCallable = callableCached();

    public static IPLocation getLocation(String ip) {
        String[] ips=ip.split(",");
        if(ips.length>1){
            ip=ips[0];
        }
        return getCallableCache(ip);
    }

    private static IPLocation getLocationByIp(String ip) {
        IPLocation location = new IPLocation();
        String returnStr = getResult(UtilConstants.SINA_API_URL+ip);
        if (!Strings.isNullOrEmpty(returnStr)) {
            returnStr = TranscodingUtils.decodeUnicode(returnStr);
            if (!Strings.isNullOrEmpty(returnStr)) {
                try {
                    JSONObject json = JSONObject.parseObject(returnStr);
                    if(json!=null){
                        String ret=json.get("ret").toString();
                        if(ret!=null&&ret.equals("1")){
                            String country = json.get("country").toString();
                            String province = json.get("province").toString();
                            String city = json.get("city").toString();
                            String isp = json.get("isp").toString();
                            if(Strings.isNullOrEmpty(city)){
                                city=city.replace("市","");
                            }
                            if(Strings.isNullOrEmpty(province)){
                                province=province.replace("省","");
                            }
                            location.setCity(city);
                            location.setCountry(country);
                            location.setProvince(province);
                            location.setIsp(isp);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        if(Strings.isNullOrEmpty(location.getCity())||Strings.isNullOrEmpty(location.getProvince())){
            location=getLocationByIpNext(ip);
        }
        return location;
    }

    private static IPLocation getLocationByIpNext(String ip) {
        IPLocation location = new IPLocation();
        String returnStr = getResult(UtilConstants.TAOBAO_API_URL+ip);
        if (!Strings.isNullOrEmpty(returnStr)) {
            returnStr = TranscodingUtils.decodeUnicode(returnStr);
            if (!Strings.isNullOrEmpty(returnStr)) {
                try {
                    JSONObject json = JSONObject.parseObject(returnStr);
                    if(json!=null){
                        String ret=json.get("code").toString();
                        if(ret!=null&&ret.equals("0")){
                            JSONObject data=json.getJSONObject("data");
                            String country = data.get("country").toString();
                            String province = data.get("region").toString();
                            String city = data.get("city").toString();
                            String isp = data.get("isp").toString();
                            if(Strings.isNullOrEmpty(city)){
                                city=city.replace("市","");
                            }
                            if(Strings.isNullOrEmpty(province)){
                                province=province.replace("省","");
                            }
                            location.setCity(city);
                            location.setCountry(country);
                            location.setProvince(province);
                            location.setIsp(isp);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return location;
    }

    /**
     * 对需要延迟处理的可以采用这个机制；(泛型的方式封装)
     * @param <K>
     * @param <V>
     * @return V
     * @throws Exception
     */
    public static <K,V> Cache<K , V> callableCached() {
        Cache<K, V> cache = CacheBuilder
                .newBuilder()
                .maximumSize(50000)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .build();
        return cache;
    }


    private static IPLocation getCallableCache(final String ip) {
        try {
            return _cacheFormCallable.get(ip, new Callable<IPLocation>() {
                @Override
                public IPLocation call() throws Exception {
                    return getLocationByIp(ip);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new IPLocation();
        }
    }

    /**
     * @param urlStr
     * 请求的地址
     * @return
     */
    private static String getResult(String urlStr) {
        URL url = null;
        HttpURLConnection connection = null;
        try {
            url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();// 新建连接实例
            connection.setConnectTimeout(2000);// 设置连接超时时间，单位毫秒
            connection.setReadTimeout(2000);// 设置读取数据超时时间，单位毫秒
            connection.setDoInput(true);// 是否打开输入流true|false
            connection.setRequestMethod("GET");// 提交方法POST|GET
            connection.setUseCaches(false);// 是否缓存true|false
            connection.connect();// 打开连接端口
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), "utf-8"));// 往对端写完数据对端服务器返回数据
            StringBuffer buffer = new StringBuffer();
            String line = "";
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            reader.close();
            return buffer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();// 关闭连接
            }
        }
        return null;
    }
}
