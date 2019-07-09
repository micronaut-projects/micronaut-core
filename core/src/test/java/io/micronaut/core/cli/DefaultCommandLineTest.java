package io.micronaut.core.cli;

import com.diffblue.deeptestutils.Reflector;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DefaultCommandLineTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Rule public final Timeout globalTimeout = new Timeout(10000);

  /* testedClasses: DefaultCommandLine */
  // Test written by Diffblue Cover.

  @Test
  public void addRemainingArgInputNotNullOutputVoid2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    final ArrayList<String> arrayList = new ArrayList<String>();
    Reflector.setField(objectUnderTest, "remainingArgs", arrayList);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);
    final String arg = "1a 2b 3c";

    // Act
    objectUnderTest.addRemainingArg(arg);

    // Assert side effects
    final ArrayList<String> arrayList1 = new ArrayList<String>();
    arrayList1.add("1a 2b 3c");
    Assert.assertEquals(arrayList1, objectUnderTest.getRemainingArgs());
  }

  // Test written by Diffblue Cover.
  @Test
  public void addSystemPropertyInputNotNullNotNullOutputVoid2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    final Properties properties = new Properties();
    Reflector.setField(objectUnderTest, "systemProperties", properties);
    Reflector.setField(objectUnderTest, "declaredOptions", null);
    final String name = "3";
    final String value = "3";

    // Act
    objectUnderTest.addSystemProperty(name, value);

    // Assert side effects
    final Properties properties1 = new Properties();
    properties1.setProperty("3", "3");
    Assert.assertEquals(properties1, objectUnderTest.getSystemProperties());
  }

  // Test written by Diffblue Cover.

  @Test
  public void addUndeclaredOptionInputNotNullOutputVoid2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);
    final String option = "3";

    // Act
    objectUnderTest.addUndeclaredOption(option);

    // Assert side effects
    final LinkedHashMap<String, Object> linkedHashMap1 = new LinkedHashMap<String, Object>();
    linkedHashMap1.put("3", true);
    Assert.assertEquals(linkedHashMap1,
                        Reflector.getInstanceField(objectUnderTest, "undeclaredOptions"));
  }

  // Test written by Diffblue Cover.

  @Test
  public void addUndeclaredOptionInputNotNullZeroOutputVoid() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    final Properties properties = new Properties();
    Reflector.setField(objectUnderTest, "systemProperties", properties);
    Reflector.setField(objectUnderTest, "declaredOptions", null);
    final String option = "2";
    final Object value = 0;

    // Act
    objectUnderTest.addUndeclaredOption(option, value);

    // Assert side effects
    final LinkedHashMap<String, Object> linkedHashMap1 = new LinkedHashMap<String, Object>();
    linkedHashMap1.put("2", 0);
    Assert.assertEquals(linkedHashMap1,
                        Reflector.getInstanceField(objectUnderTest, "undeclaredOptions"));
  }

  // Test written by Diffblue Cover.
  @Test
  public void getRawArgumentsOutputNull() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final String[] actual = objectUnderTest.getRawArguments();

    // Assert result
    Assert.assertNull(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getRemainingArgsOutputNull() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final List<String> actual = objectUnderTest.getRemainingArgs();

    // Assert result
    Assert.assertNull(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void getRemainingArgsStringOutputNotNull2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    final ArrayList<String> arrayList = new ArrayList<String>();
    Reflector.setField(objectUnderTest, "remainingArgs", arrayList);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final String actual = objectUnderTest.getRemainingArgsString();

    // Assert result
    Assert.assertEquals("", actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void getRemainingArgsStringOutputNotNull3() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    final ArrayList<String> arrayList = new ArrayList<String>();
    arrayList.add("a\'b\'c");
    Reflector.setField(objectUnderTest, "remainingArgs", arrayList);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final String actual = objectUnderTest.getRemainingArgsString();

    // Assert result
    Assert.assertEquals("a\'b\'c", actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getRemainingArgsWithOptionsStringOutputNotNull2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest =
        (DefaultCommandLine)Reflector.getInstance("io.micronaut.core.cli.DefaultCommandLine");
    final ArrayList<String> arrayList = new ArrayList<String>();
    Reflector.setField(objectUnderTest, "remainingArgs", arrayList);
    Reflector.setField(objectUnderTest, "rawArguments", null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final String actual = objectUnderTest.getRemainingArgsWithOptionsString();

    // Assert result
    Assert.assertEquals("", actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getRemainingArgsWithOptionsStringOutputNotNull3() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest =
        (DefaultCommandLine)Reflector.getInstance("io.micronaut.core.cli.DefaultCommandLine");
    final ArrayList<String> arrayList = new ArrayList<String>();
    Reflector.setField(objectUnderTest, "remainingArgs", arrayList);
    Reflector.setField(objectUnderTest, "rawArguments", null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    linkedHashMap.put("?", -4);
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final String actual = objectUnderTest.getRemainingArgsWithOptionsString();

    // Assert result
    Assert.assertEquals("-?=-4", actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getSystemPropertiesOutputNull() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final Properties actual = objectUnderTest.getSystemProperties();

    // Assert result
    Assert.assertNull(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void getUndeclaredOptionsOutputNotNull2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final Map<String, Object> actual = objectUnderTest.getUndeclaredOptions();

    // Assert result
    Assert.assertNotNull(actual);
    final LinkedHashMap linkedHashMap1 = new LinkedHashMap();
    Assert.assertEquals(linkedHashMap1, Reflector.getInstanceField(actual, "m"));
  }

  // Test written by Diffblue Cover.
  @Test
  public void lastOptionOutputNotNull() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    linkedHashMap.put(null, null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final Map.Entry<String, Object> actual = objectUnderTest.lastOption();

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertNull(Reflector.getInstanceField(actual, "value"));
    Assert.assertNull(Reflector.getInstanceField(actual, "key"));
  }

  // Test written by Diffblue Cover.

  @Test
  public void lastOptionOutputNull2() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    final LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
    Reflector.setField(objectUnderTest, "undeclaredOptions", linkedHashMap);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);

    // Act
    final Map.Entry<String, Object> actual = objectUnderTest.lastOption();

    // Assert result
    Assert.assertNull(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void setRawArgumentsInput0OutputVoid() throws InvocationTargetException {

    // Arrange
    final DefaultCommandLine objectUnderTest = new DefaultCommandLine();
    Reflector.setField(objectUnderTest, "remainingArgs", null);
    objectUnderTest.setRawArguments(null);
    Reflector.setField(objectUnderTest, "undeclaredOptions", null);
    Reflector.setField(objectUnderTest, "systemProperties", null);
    Reflector.setField(objectUnderTest, "declaredOptions", null);
    final String[] args = {};

    // Act
    objectUnderTest.setRawArguments(args);

    // Assert side effects
    Assert.assertArrayEquals(
        new String[] {}, ((String[])Reflector.getInstanceField(objectUnderTest, "rawArguments")));
  }
}
