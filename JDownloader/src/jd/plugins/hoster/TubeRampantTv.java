//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

@HostPlugin(revision = "$Revision: 33335 $", interfaceVersion = 2, names = { "tube.rampant.tv" }, urls = { "https?://(?:tube|videos)\\.rampant\\.tv/videos/[A-Za-z0-9\\-_]+\\.html" }, flags = { 0 })
public class TubeRampantTv extends PluginForHost {

    public TubeRampantTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Using playerConfig script */
    /* Tags: playerConfig.php */

    /* Extension which will be used if no correct extension is found */
    private static final String  default_Extension = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;

    private String               dllink            = null;
    private boolean              premiumonly       = false;

    @Override
    public String getAGBLink() {
        return "http://emohotties.com/contact.php";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        dllink = null;
        premiumonly = false;
        String ext = null;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getURL().contains("404.php") || br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("class=\"header playerspace\"><h2>([^<>\"]*?)</h2>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<title>([^<>\"]*?) at Rampant\\.tv Tube</title>").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        dllink = checkDirectLink(downloadLink, "directlink");
        if (dllink == null) {
            if (br.containsHTML("/premium/unleashed\\.php|\\&type=trial\\'")) {
                premiumonly = true;
                downloadLink.getLinkStatus().setStatusText("Only downloadable for premium users");
                downloadLink.setName(filename + default_Extension);
                return AvailableStatus.TRUE;
            }
            final String playerConfigUrl = br.getRegex("(https?://[A-Za-z0-9]*?\\.rampant\\.tv/playerConfig\\.php\\?[a-z0-9]+\\.(mp4|flv))").getMatch(0);
            if (playerConfigUrl == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getPage(playerConfigUrl);
            dllink = br.getRegex("defaultVideo:(https?://[^<>\"]*?);").getMatch(0);
        }
        if (dllink != null) {
            dllink = Encoding.htmlDecode(dllink);
            ext = dllink.substring(dllink.lastIndexOf("."));
            /* Make sure that we get a correct extension */
            if (ext == null || !ext.matches("\\.[A-Za-z0-9]{3,5}")) {
                ext = default_Extension;
            }
            if (!filename.endsWith(ext)) {
                filename += ext;
            }
            downloadLink.setFinalFileName(filename);
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                try {
                    con = br2.openHeadConnection(dllink);
                } catch (final BrowserException e) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                downloadLink.setProperty("directlink", dllink);
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            ext = ".mp4";
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (dllink == null && premiumonly) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } else if (dllink == null) {
            /* RTMP */
            final String rtmp_path = br.getRegex("flvMask:([^<>\"]*?);").getMatch(0);
            final String rtmp_host = br.getRegex("conn:(rtmp://[^<>\"]*?);").getMatch(0);
            if (rtmp_path == null || rtmp_host == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl = new RTMPDownload(this, downloadLink, rtmp_host);
            final jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();

            rtmp.setPlayPath("mp4:" + rtmp_path);
            rtmp.setPageUrl(this.br.getURL());
            rtmp.setSwfVfy("https://static.rampant.tv/swf/player.swf");
            rtmp.setApp("tubevideo/");
            rtmp.setUrl(rtmp_host);
            rtmp.setResume(true);

            ((RTMPDownload) dl).startDownload();
        } else {
            /* HTTP */
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, free_resume, free_maxchunks);
            if (dl.getConnection().getContentType().contains("html")) {
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                }
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "/");
        output = output.replace("\\", "");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.UnknownPornScript4;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
