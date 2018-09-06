package jd.controlling.faviconcontroller;

import java.util.ArrayList;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultLongValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.RequiresRestart;

public interface FavIconsConfig extends ConfigInterface {

    @DefaultIntValue(12)
    @AboutConfig
    @RequiresRestart("A RegardsDownloader restart is required")
    @DescriptionForConfigEntry("max. number of favicon threads")
    int getMaxThreads();

    void setMaxThreads(int i);

    @DefaultIntValue(20000)
    @AboutConfig
    @RequiresRestart("A RegardsDownloader restart is required")
    @DescriptionForConfigEntry("max. time in ms before killing an idle favicon thread")
    int getThreadKeepAlive();

    void setThreadKeepAlive(int i);

    @DefaultLongValue(0)
    @AboutConfig
    long getLastRefresh();

    void setLastRefresh(long t);

    @AboutConfig
    ArrayList<String> getFailedHosts();

    void setFailedHosts(ArrayList<String> hosts);

}
