package org.jdownloader.captcha.v2.solver.imagetyperz;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.jdownloader.captcha.v2.AbstractResponse;
import org.jdownloader.captcha.v2.Challenge;
import org.jdownloader.captcha.v2.SolverStatus;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.AbstractRecaptcha2FallbackChallenge;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.RecaptchaV2Challenge;
import org.jdownloader.captcha.v2.challenge.stringcaptcha.BasicCaptchaChallenge;
import org.jdownloader.captcha.v2.solver.CESChallengeSolver;
import org.jdownloader.captcha.v2.solver.CESSolverJob;
import org.jdownloader.captcha.v2.solver.jac.SolverException;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.staticreferences.CFG_IMAGE_TYPERZ;
import org.seamless.util.io.IO;

import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.http.requests.FormData;
import jd.http.requests.PostFormDataRequest;

public class ImageTyperzCaptchaSolver extends CESChallengeSolver<String> {

    private final ImageTyperzConfigInterface      config;
    private static final ImageTyperzCaptchaSolver INSTANCE   = new ImageTyperzCaptchaSolver();
    private final ThreadPoolExecutor              threadPool = new ThreadPoolExecutor(0, 1, 30000, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(), Executors.defaultThreadFactory());
    private final LogSource                       logger;

    public static ImageTyperzCaptchaSolver getInstance() {
        return INSTANCE;
    }

    @Override
    public Class<String> getResultType() {
        return String.class;
    }

    @Override
    public ImageTyperzSolverService getService() {
        return (ImageTyperzSolverService) super.getService();
    }

    private ImageTyperzCaptchaSolver() {
        super(new ImageTyperzSolverService(), Math.max(1, Math.min(25, JsonConfig.create(ImageTyperzConfigInterface.class).getThreadpoolSize())));
        getService().setSolver(this);
        config = JsonConfig.create(ImageTyperzConfigInterface.class);
        logger = LogController.getInstance().getLogger(ImageTyperzCaptchaSolver.class.getName());

        threadPool.allowCoreThreadTimeOut(true);

    }

    @Override
    public boolean canHandle(Challenge<?> c) {
        if (c instanceof RecaptchaV2Challenge || c instanceof AbstractRecaptcha2FallbackChallenge) {
            return true;
        }
        return c instanceof BasicCaptchaChallenge && super.canHandle(c);
    }

    protected void solveBasicCaptchaChallenge(CESSolverJob<String> job, BasicCaptchaChallenge challenge) throws InterruptedException, SolverException {
        job.showBubble(this);
        checkInterruption();
        job.getChallenge().sendStatsSolving(this);
        URLConnectionAdapter conn = null;
        try {
            final Browser br = new Browser();
            br.setReadTimeout(5 * 60000);
            // Put your CAPTCHA image file, file object, input stream,
            // or vector of bytes here:
            job.setStatus(SolverStatus.SOLVING);
            final PostFormDataRequest r;
            if (challenge instanceof AbstractRecaptcha2FallbackChallenge) {
                r = new PostFormDataRequest("http://captchatypers.com/Forms/UploadGoogleCaptcha.ashx");
            } else {
                r = new PostFormDataRequest("http://captchatypers.com/Forms/UploadFileAndGetTextNew.ashx");
            }
            r.addFormData(new FormData("action", "UPLOADCAPTCHA"));
            r.addFormData(new FormData("username", (config.getUserName())));
            r.addFormData(new FormData("password", (config.getPassword())));
            r.addFormData(new FormData("chkCase", "0"));

            final byte[] data;
            if (challenge instanceof AbstractRecaptcha2FallbackChallenge) {
                data = challenge.getAnnotatedImageBytes();
            } else {
                data = IO.readBytes(challenge.getImageFile());
            }
            r.addFormData(new FormData("file", org.appwork.utils.encoding.Base64.encodeToString(data, false)));

            conn = br.openRequestConnection(r);

            // ERROR: INVALID_REQUEST = It will be returned when the program tries to send the invalid request.
            // ERROR: INVALID_USERNAME = If the username is not provided, this will be returned.
            // ERROR: INVALID_PASSWORD = if the password is not provide, this will be returned.
            // ERROR: INVALID_IMAGE_FILE = No file uploaded or No image type file uploaded.
            // ERROR: AUTHENTICATION_FAILED = Provided username and password are invalid.
            // ERROR: INVALID_IMAGE_SIZE_30_KB = The uploading image file must be 30 KB.
            // ERROR: UNKNOWN = Unknown error happened, close the program and reopen.
            // ERROR: NOT_DECODED = The captcha is timedout
            // if success the captcha decoded text along with image id will be returned.
            // Example of output: "1245986|HGFJD"
            // Using the captcha id you can set the captcha as bad.

            // Poll for the uploaded CAPTCHA status.
            br.loadConnection(conn);
            final String response = br.toString();
            if (response.startsWith("ERROR: ")) {
                throw new WTFException(response.substring("ERROR: ".length()));
            }
            final String[] result = br.getRegex("(\\d+)\\|(.*)").getRow(0);
            if (result != null) {
                final AbstractResponse<String> answer = challenge.parseAPIAnswer(result[1], this);
                job.getLogger().info("CAPTCHA " + challenge.getImageFile() + " solved: " + response);
                job.setAnswer(new ImageTyperzResponse(challenge, this, result[0], answer.getValue(), answer.getPriority()));
            } else {
                job.getLogger().info("Failed solving CAPTCHA");
                throw new SolverException("Failed:" + response);
            }
        } catch (Exception e) {
            job.getChallenge().sendStatsError(this, e);
            job.getLogger().log(e);
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (final Throwable e) {
            }
        }
    }

    protected boolean validateLogins() {
        if (!CFG_IMAGE_TYPERZ.ENABLED.isEnabled()) {
            return false;
        }
        if (StringUtils.isEmpty(CFG_IMAGE_TYPERZ.USER_NAME.getValue())) {
            return false;
        }
        if (StringUtils.isEmpty(CFG_IMAGE_TYPERZ.PASSWORD.getValue())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean setInvalid(final AbstractResponse<?> response) {
        if (config.isFeedBackSendingEnabled() && response instanceof ImageTyperzResponse) {
            threadPool.execute(new Runnable() {

                @Override
                public void run() {
                    URLConnectionAdapter conn = null;
                    try {
                        final String captcha = ((ImageTyperzResponse) response).getCaptchaID();
                        final Challenge<?> challenge = response.getChallenge();
                        if (challenge instanceof BasicCaptchaChallenge) {
                            final Browser br = new Browser();
                            final PostFormDataRequest r = new PostFormDataRequest("http://captchatypers.com/Forms/SetBadImage.ashx");
                            final String userName = config.getUserName();
                            r.addFormData(new FormData("action", "SETBADIMAGE"));
                            r.addFormData(new FormData("username", userName));
                            r.addFormData(new FormData("password", config.getPassword()));
                            r.addFormData(new FormData("imageID", captcha));
                            conn = br.openRequestConnection(r);
                            br.loadConnection(conn);
                        }
                        // // Report incorrectly solved CAPTCHA if neccessary.
                        // // Make sure you've checked if the CAPTCHA was in fact
                        // // incorrectly solved, or else you might get banned as
                        // // abuser.
                        // Client client = getClient();
                    } catch (final Throwable e) {
                        logger.log(e);
                    } finally {
                        try {
                            if (conn != null) {
                                conn.disconnect();
                            }
                        } catch (final Throwable e) {
                        }
                    }
                }
            });
            return true;
        }
        return false;
    }

    public ImageTyperzAccount loadAccount() {
        final ImageTyperzAccount ret = new ImageTyperzAccount();
        URLConnectionAdapter conn = null;
        try {
            final Browser br = new Browser();
            final PostFormDataRequest r = new PostFormDataRequest("http://captchatypers.com/Forms/RequestBalance.ashx");
            final String userName = config.getUserName();
            r.addFormData(new FormData("action", "REQUESTBALANCE"));
            r.addFormData(new FormData("username", userName));
            r.addFormData(new FormData("password", config.getPassword()));

            conn = br.openRequestConnection(r);
            br.loadConnection(conn);
            final String response = br.toString();
            if (response.startsWith("ERROR: ")) {
                throw new WTFException(response.substring("Error: ".length()));
            }
            ret.setUserName(userName);
            ret.setBalance(100 * Double.parseDouble(response));
        } catch (Exception e) {
            logger.log(e);
            ret.setError(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (final Throwable e) {
            }
        }
        return ret;

    }

}
