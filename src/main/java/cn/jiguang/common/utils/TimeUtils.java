package cn.jiguang.common.utils;


import java.text.ParseException;
import java.text.SimpleDateFormat;

public class TimeUtils {

	private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };
    private static final ThreadLocal<SimpleDateFormat> TIME_ONLY_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss");
        }
    };

    static {
        DATE_FORMAT.get().setLenient(false);
        TIME_ONLY_FORMAT.get().setLenient(false);
    }

    public static boolean isDateFormat(String time) {
        try {
            DATE_FORMAT.get().parse(time);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public static boolean isTimeFormat(String time) {
        try{
            TIME_ONLY_FORMAT.get().parse(time);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

}
