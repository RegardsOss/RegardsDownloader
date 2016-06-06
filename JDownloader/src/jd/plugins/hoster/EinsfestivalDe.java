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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision: 32687 $", interfaceVersion = 3, names = { "einsfestival.de" }, urls = { "https?://(?:www\\.)?einsfestival\\.de/[^/]+/[a-z0-9]+\\.jsp\\?vid=\\d+" }, flags = { 0 })
public class EinsfestivalDe extends PluginForHost {

    public EinsfestivalDe(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;

    private String               DLLINK            = null;
    private boolean              SERVERERROR       = false;

    @Override
    public String getAGBLink() {
        return "http://www.einsfestival.de/impressum/impressum.jsp";
    }

    /** The video-CMS they use is just a huge mess! */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        final String vid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
        DLLINK = null;
        SERVERERROR = false;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        downloadLink.setName(vid);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">Es ist ein Fehler aufgetreten|>Bitte versuche es erneut oder später noch einmal")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String title_subtitle = null;
        String thisvideo_src = this.br.getRegex("arrVideos\\[" + vid + "\\] = \\{(.*?)\\}").getMatch(0);
        if (thisvideo_src != null) {
            final String title_subtitle_alternative = new Regex(thisvideo_src, "zmdbTitle: \\'([^<>\"\\']+)\\'").getMatch(0);
            title_subtitle = new Regex(thisvideo_src, "startAlt: \\'([^<>\"\\']+)\\'").getMatch(0);
            /* Avoid extremely long filenames with unneeded information! */
            if (title_subtitle == null || title_subtitle.matches(".+(SENDER:|SENDETITEL:|UNTERTITEL:).+")) {
                title_subtitle = title_subtitle_alternative;
            }
        } else {
            thisvideo_src = this.br.toString();
        }
        // String server_rtmp = new Regex(thisvideo_src, "videoServer: \\'(rtmp://[^<>\"\\']+)\\'").getMatch(0);
        // if (server_rtmp == null) {
        // /* 2016-02-02 */
        // server_rtmp = "rtmp://gffstream.fcod.llnwd.net/a792/e2";
        // }
        String server_http = new Regex(thisvideo_src, "videoServerHttp: \\'(http://[^<>\"\\']+)\\'").getMatch(0);
        if (server_http == null) {
            /* 2016-02-02 */
            server_http = "http://http-ras.wdr.de/";
        }
        String dslsrc = new Regex(thisvideo_src, "dslSrc: \\'(/[^<>\"\\']+\\.mp4)\\'").getMatch(0);
        if (dslsrc == null) {
            /* Fallback for older links ... */
            dslsrc = br.getRegex("dslSrc=rtmpe?://gffstream[^<>\"\\']+(/CMS[^<>\"\\'\\&]+\\.mp4)\\&").getMatch(0);
            if (dslsrc == null) {
                dslsrc = br.getRegex("dslSrc: \\'(/[^<>\"\\']+\\.mp4)\\'").getMatch(0);
            }
        }
        final String isdnsrc = new Regex(thisvideo_src, "isdnSrc: \\'(/[^<>\"\\']+\\.mp4)\\'").getMatch(0);
        String date = br.getRegex("name=\\'VideoDate\\' content=\\'([^<>\"]*?)\\'").getMatch(0);
        if (date == null) {
            date = this.br.getRegex("<meta name=\\'DC\\.Date\\' content=\\'([^<>\"\\']+)\\'").getMatch(0);
        }
        String title = br.getRegex("<title>([^<>]*?) \\| Einsfestival</title>").getMatch(0);
        if (title == null) {
            title = br.getRegex("class=\"videoInfoHead\">([^<>]*?)<").getMatch(0);
        }
        if (title == null) {
            title = br.getRegex("itemprop=\"name\">([^<>\"]*?)<").getMatch(0);
        }
        if (title == null) {
            title = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        }
        if (title == null || date == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String date_formatted = formatDate(date);
        String filename = date_formatted + "_einsfestival_" + title;
        if (title_subtitle != null) {
            filename += " - " + title_subtitle;
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        final String ext = ".mp4";
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        if (server_http != null && (dslsrc != null || isdnsrc != null)) {
            if (dslsrc != null) {
                DLLINK = server_http + dslsrc;
            } else {
                DLLINK = server_http + isdnsrc;
            }
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(DLLINK);
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } else {
                    SERVERERROR = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (this.br.containsHTML("id=\"fskInfo\"")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Sendung aus Jugendschutzgründen aktuell nicht verfügbar", 60 * 60 * 1000l);
        } else if (SERVERERROR) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error - video offline/not playable", 30 * 60 * 1000l);
        }
        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String formatDate(String input) {
        /* 2015-06-23T20:15+02:00 --> 2015-06-23T20:15:00+0200 */
        input = input.substring(0, input.lastIndexOf(":")) + "00";
        final long date = TimeFormatter.getMilliSeconds(input, "yyyy-MM-dd'T'HH:mmZZ", Locale.GERMAN);
        String formattedDate = null;
        final String targetFormat = "yyyy-MM-dd";
        Date theDate = new Date(date);
        try {
            final SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
            formattedDate = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent input error killing plugin */
            formattedDate = input;
        }
        return formattedDate;
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
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
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}