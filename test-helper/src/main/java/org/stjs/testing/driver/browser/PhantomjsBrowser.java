package org.stjs.testing.driver.browser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.runners.model.InitializationError;
import org.openqa.selenium.browserlaunchers.locators.BrowserInstallation;
import org.openqa.selenium.browserlaunchers.locators.SingleBrowserLocator;
import org.stjs.generator.BridgeClass;
import org.stjs.generator.ClassWithJavascript;
import org.stjs.generator.DependencyCollection;
import org.stjs.generator.Generator;
import org.stjs.testing.annotation.HTMLFixture;
import org.stjs.testing.annotation.Scripts;
import org.stjs.testing.annotation.ScriptsAfter;
import org.stjs.testing.annotation.ScriptsBefore;
import org.stjs.testing.driver.AsyncProcess;
import org.stjs.testing.driver.DriverConfiguration;
import org.stjs.testing.driver.HttpLongPollingServer;
import org.stjs.testing.driver.MultiTestMethod;
import org.stjs.testing.driver.StreamUtils;
import org.stjs.testing.driver.TestResult;

import com.google.common.base.Strings;
import com.sun.net.httpserver.HttpExchange;

@SuppressWarnings({ "restriction" /* for HttpExchange */, "deprecation" /* for @Scripts */})
public class PhantomjsBrowser extends LongPollingBrowser {

	public static final String PROP_PHANTOMJS_BIN = "phantomjs.bin";

	private File tempBootstrapJs;

	public PhantomjsBrowser(DriverConfiguration config) {
		super(config);
	}

	@Override
	public void doStart() throws InitializationError {
		this.registerWithLongPollingServer();
		try {
			// We first need to extract phantomjs-bootstrap.js to the temp directory, because phantomjs
			// can only be started with a file on the local filesystem as argument
			tempBootstrapJs = unpackBootstrap();

			String executableName = getConfig().getProperty(PROP_PHANTOMJS_BIN);
			if (executableName == null) {
				BrowserInstallation installation = new Locator().findBrowserLocation();
				if (installation == null) {
					throw new InitializationError( //
							"phantomjs could not be found in the path!\n"
									+ "Please add the directory containing 'phantomjs' or 'phantomjs.exe' to your PATH environment\n"
									+ "variable, or explicitly specify a path to phantomjs in stjs-test.properties like this:\n"
									+ PROP_PHANTOMJS_BIN + "=/blah/blah/phantomjs");
				}
				executableName = installation.launcherFilePath();
			}
			new ProcessBuilder( //
					executableName, //
					"--web-security=no", //
					tempBootstrapJs.getAbsolutePath(), //
					Long.toString(getId()), //
					getConfig().getServerURL().toString()).start();

			if (getConfig().isDebugEnabled()) {
				System.out.println("Started phantomjs");
			}
		} catch (IOException e) {
			throw new InitializationError(e);
		}
	}

	@Override
	public void sendTestFixture(MultiTestMethod meth, HttpExchange exchange) throws IOException, URISyntaxException {

		Class<?> testClass = meth.getTestClass().getJavaClass();
		Method method = meth.getMethod().getMethod();
		ClassWithJavascript stjsClass = new Generator().getExistingStjsClass(getConfig().getClassLoader(), testClass);

		final HTMLFixture htmlFixture = testClass.getAnnotation(HTMLFixture.class);

		final Scripts addedScripts = testClass.getAnnotation(Scripts.class);
		final ScriptsBefore addedScriptsBefore = testClass.getAnnotation(ScriptsBefore.class);
		final ScriptsAfter addedScriptsAfter = testClass.getAnnotation(ScriptsAfter.class);

		StringBuilder resp = new StringBuilder(8192);
		resp.append("<html>\n");
		resp.append("<head>\n");
		addScript(resp, "/stjs.js");
		addScript(resp, "/junit.js");

		resp.append("<script language='javascript'>stjs.mainCallDisabled=true;</script>\n");

		// scripts added explicitly
		if (addedScripts != null) {
			for (String script : addedScripts.value()) {
				addScript(resp, script);
			}
		}
		// scripts before - new style
		if (addedScriptsBefore != null) {
			for (String script : addedScriptsBefore.value()) {
				addScript(resp, script);
			}
		}

		Set<URI> jsFiles = new LinkedHashSet<URI>();
		for (ClassWithJavascript dep : new DependencyCollection(stjsClass).orderAllDependencies(getConfig()
				.getClassLoader())) {

			if (addedScripts != null && dep instanceof BridgeClass) {
				// bridge dependencies are not added when using @Scripts
				System.out
						.println("WARNING: You're using @Scripts deprecated annotation that disables the automatic inclusion of the Javascript files of the bridges you're using! "
								+ "Please consider using @ScriptsBefore and/or @ScriptsAfter instead.");
				continue;
			}
			for (URI file : dep.getJavascriptFiles()) {
				jsFiles.add(file);
			}
		}

		for (URI file : jsFiles) {
			addScript(resp, file.toString());
		}

		// scripts after - new style
		if (addedScriptsAfter != null) {
			for (String script : addedScriptsAfter.value()) {
				addScript(resp, script);
			}
		}
		resp.append("<script language='javascript'>\n");
		resp.append("  onload=function(){\n");
		resp.append("    console.error(document.getElementsByTagName('html')[0].innerHTML);\n");

		// Adapter between generated assert (not global) and JS-test-driver assert (which is a
		// set of global methods)
		resp.append("    Assert=window;\n");

		String testedClassName = testClass.getSimpleName();

		resp.append("    try{\n");
		resp.append("      new " + testedClassName + "()." + method.getName() + "();\n");
		resp.append("      parent.reportResultAndRunNextTest('OK');\n");
		resp.append("    }catch(ex){\n");
		resp.append("      parent.reportResultAndRunNextTest(ex, ex.location);\n");
		resp.append("    }\n");
		resp.append("  }\n");
		resp.append("</script>\n");
		resp.append("</head>\n");
		resp.append("<body>\n");
		if (htmlFixture != null) {
			if (!Strings.isNullOrEmpty(htmlFixture.value())) {
				resp.append(htmlFixture.value());

			} else if (!Strings.isNullOrEmpty(htmlFixture.url())) {
				StringWriter writer = new StringWriter();
				StreamUtils.copy(getConfig().getClassLoader(), htmlFixture.url(), writer);
				resp.append(writer.toString());
			}
		}
		resp.append("</body>\n");
		resp.append("</html>\n");

		byte[] response = resp.toString().getBytes("UTF-8");
		exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);

		OutputStream output = exchange.getResponseBody();
		output.write(response);
		output.flush();
	}

	@Override
	public void sendNoMoreTestFixture(HttpExchange exchange) throws IOException {
		byte[] response = "<html><head><script language='javascript'>parent.phantom.exit()</script></head></html>"
				.getBytes("UTF-8");
		exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);

		OutputStream output = exchange.getResponseBody();
		output.write(response);
		output.flush();
	}

	private void addScript(StringBuilder builder, String script) throws IOException {
		// remove wrong leading classpath://
		String cleanScript = script.replace("classpath://", "/");
		// add a slash to prevent the browser to interpret the scheme
		builder.append("<script src='" + cleanScript + "'></script>\n");
	}

	@Override
	public void stop() {
		// phantomJS automatically stops when the noMoreTests fixture is sent
		tempBootstrapJs.delete();
	}

	private File unpackBootstrap() throws IOException {
		File tmp = File.createTempFile("phantomjs", null);
		InputStream in = this.getClass().getResourceAsStream("/phantomjs-bootstrap.js");
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
		byte[] buffer = new byte[8192];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) > 0) {
			out.write(buffer, 0, bytesRead);
		}
		in.close();
		out.close();
		return tmp;
	}

	@Override
	public TestResult buildResult(Map<String, String> queryStringParameters, HttpExchange exchange) {
		String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
		String result = queryStringParameters.get("result");
		String location = queryStringParameters.get("location");

		if (getConfig().isDebugEnabled()) {
			System.out.println("Result was: " + result + ", at " + location + ", from " + userAgent);
		}

		return new TestResult(userAgent, result, location);
	}

	@Override
	public Set<Class<? extends AsyncProcess>> getSharedDependencies() {
		Set<Class<? extends AsyncProcess>> dep = new HashSet<Class<? extends AsyncProcess>>();
		dep.add(HttpLongPollingServer.class);
		return dep;
	}

	private static class Locator extends SingleBrowserLocator {

		@Override
		protected String[] standardlauncherFilenames() {
			return new String[] { "phantomjs", "phantomjs.exe" };
		}

		@Override
		protected String[] usualLauncherLocations() {
			// phantomjs doesn't have a proper installer, so there really isn't any usual
			// location where it would be. Except maybe on linux versions that use package
			// managers
			return new String[] { "/usr/bin" };
		}

		@Override
		protected String seleniumBrowserName() {
			// not useful in stjs, but required by selenium
			return "phantomjs";
		}

		@Override
		protected String browserPathOverridePropertyName() {
			// not useful in stjs, but required by selenium
			return "phantomjs";
		}

		@Override
		protected String browserName() {
			// not useful in stjs, but required by selenium
			return "phantomjs";
		}
	}
}