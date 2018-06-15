package org.houxg.leamonax.utils;

import com.elvishew.xlog.XLog;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private String tag;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static Logger getInstance(String tag) {
        Logger logger = new Logger();
        logger.tag = tag;
        return logger;
    }


    public void info(String msg){
        XLog.i(MessageFormat.format("{0} - {1} -- {2}", sdf.format(new Date()), tag, msg));
    }


}
