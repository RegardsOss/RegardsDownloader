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

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DummyScriptEnginePlugin;

@DecrypterPlugin(revision = "$Revision: 33293 $", interfaceVersion = 3, names = { "younow.com" }, urls = { "https?://(?:www\\.)?younow\\.com/[^/]+(?:/\\d+)?" }, flags = { 0 })
public class YounowComChannel extends PluginForDecrypt {

    public YounowComChannel(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String username = new Regex(parameter, "younow\\.com/([^/]+)").getMatch(0);
        String fpName = null;
        if (parameter.matches("https?://(?:www\\.)?younow\\.com/[^/]+/\\d+")) {
            decryptedLinks.add(this.createDownloadlink(parameter.replace("younow.com/", "younowdecrypted.com/")));
        } else {
            fpName = username;
            br.getPage(parameter);
            br.getPage("https://www.younow.com/php/api/broadcast/info/user=" + username + "/curId=0");
            int addedlinks = 0;
            int addedlinks_temp = 0;
            String userid = this.br.getRegex("\"userId\":\"(\\d+)\"").getMatch(0);
            if (userid == null) {
                userid = getJson("userId");
            }
            if (inValidate(userid)) {
                /* Probably that user does not exist */
                return decryptedLinks;
            }
            do {
                addedlinks_temp = 0;
                this.br.getHeaders().put("Accept", "application/json, text/plain, */*");
                this.br.getHeaders().put("Referer", "https://www.younow.com/" + username + "/channel");
                // this.br.getHeaders().put("Origin", "https://www.younow.com");
                br.getPage("https://cdn2.younow.com/php/api/post/getBroadcasts/channelId=" + userid + "/startFrom=" + (addedlinks + 1));
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                final ArrayList<Object> ressourcelist = (ArrayList) entries.get("posts");
                if (ressourcelist == null) {
                    break;
                }
                for (final Object objecto : ressourcelist) {
                    addedlinks++;
                    addedlinks_temp++;
                    entries = (LinkedHashMap<String, Object>) objecto;
                    entries = (LinkedHashMap<String, Object>) entries.get("media");
                    final long mediatype = DummyScriptEnginePlugin.toLong(entries.get("type"), 0);
                    if (mediatype != 5) {
                        /* Skip non-video-content */
                        continue;
                    }
                    entries = (LinkedHashMap<String, Object>) entries.get("broadcast");
                    final long broadcastID = DummyScriptEnginePlugin.toLong(entries.get("broadcastId"), 0);
                    final String broadcasttitle = jd.plugins.hoster.YounowCom.getbroadcastTitle(entries);
                    if (broadcastID == 0 || inValidate(broadcasttitle)) {
                        continue;
                    }
                    final DownloadLink dl = this.createDownloadlink("https://www.younowdecrypted.com/" + username + "/" + broadcastID);
                    String temp_filename;
                    if (!inValidate(broadcasttitle)) {
                        /* We might not be able to easily get this information later --> Save it on our DownloadLink */
                        dl.setProperty("decryptedbroadcasttitle", broadcasttitle);
                        temp_filename = username + "_" + broadcastID + "_" + broadcasttitle;
                    } else {
                        temp_filename = username + "_" + broadcastID;
                    }
                    temp_filename = encodeUnicode(temp_filename) + ".mp4";
                    dl.setName(temp_filename);
                    dl.setAvailable(true);
                    decryptedLinks.add(dl);
                    distribute(dl);
                }
            } while (addedlinks_temp == 10);
        }

        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }

    /**
     * Wrapper<br/>
     * Tries to return value of key from JSon response, from String source.
     *
     * @author raztoki
     * */
    private String getJson(final String source, final String key) {
        return jd.plugins.hoster.K2SApi.JSonUtils.getJson(source, key);
    }

    /**
     * Wrapper<br/>
     * Tries to return value of key from JSon response, from default 'br' Browser.
     *
     * @author raztoki
     * */
    private String getJson(final String key) {
        return jd.plugins.hoster.K2SApi.JSonUtils.getJson(br.toString(), key);
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
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

}
