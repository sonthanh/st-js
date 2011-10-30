package org.stjs.generator.writer.methods;

import static org.stjs.generator.utils.GeneratorTestHelper.assertCodeContains;
import static org.stjs.generator.utils.GeneratorTestHelper.generate;

import org.junit.Test;
import org.stjs.generator.JavascriptGenerationException;


public class MethodsGeneratorTest {
	@Test
	public void testPublicInstanceMethod() {
		assertCodeContains(Methods1.class, "Methods1.prototype.method = function(arg1,arg2){return 0;};");
	}

	@Test
	public void testPrivateInstanceMethod() {
		// same as public
		assertCodeContains(Methods2.class, "Methods2.prototype.method = function(arg1,arg2){");
	}

	@Test
	public void testPublicStaticMethod() {
		assertCodeContains(Methods3.class, "Methods3.method = function(arg1,arg2){");
	}

	@Test
	public void testPrivateStaticMethod() {
		assertCodeContains(Methods4.class, "Methods4.method = function(arg1,arg2){");
	}

	@Test
	public void testMainMethod() {
		// should generate the call to the main method
		assertCodeContains(Methods5.class, "if (!stjs.mainCallDisabled) Methods5.main();");
	}

	@Test
	public void testConstructor() {
		assertCodeContains(Methods6.class, "Methods6=function(arg){");
	}

	@Test
	public void testSpecialThis() {
		// the special parameter THIS should not be added
		assertCodeContains(Methods7.class, "Methods7.prototype.method=function(arg2){");
	}

	@Test
	public void testAdapter() {
		assertCodeContains(Methods8.class, "(10).toFixed(2)");
	}

	@Test(expected = JavascriptGenerationException.class)
	public void testVarArgsMethod1() {
		// only one var arg argument is allowed and the name should be "arguments" -> like the js variable
		generate(Methods9.class);
	}

	@Test(expected = JavascriptGenerationException.class)
	public void testVarArgsMethod2() {
		// only one var arg argument is allowed and the name should be "arguments" -> like the js variable
		generate(Methods10.class);
	}

	@Test
	public void testVarArgsMethod3() {
		// only one var arg argument is allowed and the name should be "arguments" -> like the js variable
		assertCodeContains(Methods11.class, "Methods11.prototype.method=function(arguments){}");
	}

	@Test
	public void testInterfaceImplResolution() {
		assertCodeContains(Methods12.class, "method(c);");
	}

	@Test
	public void testWildcardResolution() {
		assertCodeContains(Methods13.class, "m.parent().parent()");
	}
}