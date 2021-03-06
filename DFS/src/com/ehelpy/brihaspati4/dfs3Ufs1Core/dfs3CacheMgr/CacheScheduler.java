package com.ehelpy.brihaspati4.dfs3Ufs1Core.dfs3CacheMgr;
import com.ehelpy.brihaspati4.dfs3Ufs1Core.dfs3Mgr.DFS3Config;
import com.ehelpy.brihaspati4.simulateGC.communication.Receiver;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.awt.Toolkit;

/**
 * Schedule a task that executes once every second.
 */

public class CacheScheduler {
    static DFS3Config dfs3_ufs1 = DFS3Config.getInstance();
    Toolkit toolkit;
    Timer timer;
    private static final Logger log = Logger.getLogger(Receiver.class.getName());

    public CacheScheduler() {
        toolkit = Toolkit.getDefaultToolkit();
        timer = new Timer();
        timer.schedule(new RemindTask(),
                0,        //initial delay
                1000*60*60*24);  //subsequent rate - 24 hours
    }

    static class RemindTask extends TimerTask {

        public void run() {
            Path cachePath= Paths.get(dfs3_ufs1.getDfsCache());
            try {
                //int i = CacheOldDelete.oldDelete(cachePath,15);
                //System.out.println(i+" Files older than 15 days deleted from Cache");
                int j = CacheSizeDelete.sizedelete(cachePath, dfs3_ufs1.getLocalCacheSize());
                log.debug(j+" Older files exceeding cache size deleted from Cache");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public static void scheduler() {
        //System.out.println("About to schedule task.");
        new CacheScheduler();
        log.debug("Cache clean up task scheduled.");
    }
}