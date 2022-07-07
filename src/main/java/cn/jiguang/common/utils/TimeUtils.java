package cn.jiguang.common.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class TimeUtils {

    public static boolean isDateFormat(String time) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            simpleDateFormat.setLenient(false);
            simpleDateFormat.parse(time);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public static boolean isTimeFormat(String time) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            simpleDateFormat.setLenient(false);
            simpleDateFormat.parse(time);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

}
