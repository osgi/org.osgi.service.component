/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.test.cases.component.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentException;
import org.osgi.service.component.ComponentFactory;
import org.osgi.test.cases.component.service.ObjectProvider1;
import org.osgi.test.cases.component.types.AnnotationMember;
import org.osgi.test.cases.component.types.ClassMember;
import org.osgi.test.cases.component.types.Coercion;
import org.osgi.test.cases.component.types.EnumMember;
import org.osgi.test.cases.component.types.NameMapping14;
import org.osgi.test.cases.component.types.NameMapping;
import org.osgi.test.cases.component.types.SingleElement;
import org.osgi.test.cases.component.types.TestEnum;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.tracker.ServiceTracker;

public class ComponentPropertyTypeTestCase {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;
	private static int SLEEP = 1000;

	public ComponentPropertyTypeTestCase() {
	}

	@BeforeEach
	public void setUp() throws Exception {
		String sleepTimeString = AccessController.doPrivileged((PrivilegedAction<String>) () -> context.getProperty("osgi.tc.component.sleeptime"));
		int sleepTime = SLEEP;
		if (sleepTimeString != null) {
			try {
				sleepTime = Integer.parseInt(sleepTimeString);
			} catch (Exception e) {
				e.printStackTrace();
				System.out
						.println("Error while parsing sleep value! The default one will be used : "
								+ SLEEP);
			}
			if (sleepTime < 100) {
				System.out.println("The sleep value is too low : " + sleepTime
						+ " ! The default one will be used : " + SLEEP);
			} else {
				SLEEP = sleepTime;
			}
		}
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testNameMapping130() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.NameMappingComponent))");
			ServiceTracker<ObjectProvider1<NameMapping>, ObjectProvider1<NameMapping>> providerTracker = new ServiceTracker<ObjectProvider1<NameMapping>, ObjectProvider1<NameMapping>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<NameMapping> p = providerTracker.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				NameMapping config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				assertThat(config.myProperty143()).as("property has wrong value").isEqualTo("xml/myProperty143");
				assertThat(config.$new()).as("property has wrong value").isEqualTo("xml/new");
				assertThat(config.my$$prop()).as("property has wrong value").isEqualTo("xml/my$prop");
				assertThat(config.dot_prop()).as("property has wrong value").isEqualTo("xml/dot.prop");
				assertThat(config._secret()).as("property has wrong value").isEqualTo("xml/.secret");
				assertThat(config.another__prop()).as("property has wrong value").isEqualTo("xml/another_prop");
				assertThat(config.three___prop()).as("property has wrong value").isEqualTo("xml/three_.prop");
				assertThat(config.four_$__prop()).as("property has wrong value").isEqualTo("xml/four._prop");
				assertThat(config.five_$_prop()).as("property has wrong value").isEqualTo("xml/five..prop");

			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testNameMapping140() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&("
					+ Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")("
					+ ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.NameMappingComponent14))");
			ServiceTracker<ObjectProvider1<NameMapping14>,ObjectProvider1<NameMapping14>> providerTracker = new ServiceTracker<ObjectProvider1<NameMapping14>,ObjectProvider1<NameMapping14>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<NameMapping14> p = providerTracker
						.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				NameMapping14 config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				assertThat(config.myProperty143()).as("property has wrong value").isEqualTo("xml/myProperty143");
				assertThat(config.$new()).as("property has wrong value").isEqualTo("xml/new");
				assertThat(config.my$$prop()).as("property has wrong value").isEqualTo("xml/my$prop");
				assertThat(config.dot_prop()).as("property has wrong value").isEqualTo("xml/dot.prop");
				assertThat(config.another__prop()).as("property has wrong value").isEqualTo("xml/another_prop");
				assertThat(config.three___prop()).as("property has wrong value").isEqualTo("xml/three_.prop");
				assertThat(config.four_$__prop()).as("property has wrong value").isEqualTo("xml/four._prop");
				assertThat(config.five_$_prop()).as("property has wrong value").isEqualTo("xml/five..prop");
				assertThat(config.six$_$prop()).as("property has wrong value").isEqualTo("xml/six-prop");
				assertThat(config.seven$$_$prop()).as("property has wrong value").isEqualTo("xml/seven$.prop");

			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testSingleElement140() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&("
					+ Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")("
					+ ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.SingleElementComponent))");
			ServiceTracker<ObjectProvider1<SingleElement>,ObjectProvider1<SingleElement>> providerTracker = new ServiceTracker<ObjectProvider1<SingleElement>,ObjectProvider1<SingleElement>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<SingleElement> p = providerTracker
						.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				SingleElement config = p.get1();
				assertThat(config).as("missing config").isNotNull();
				assertThat(config.value()).as("property has wrong value").isEqualTo("xml/single");

			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testAnnotationMember130() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.AnnotationMemberComponent))");
			ServiceTracker<ObjectProvider1<AnnotationMember>, ObjectProvider1<AnnotationMember>> providerTracker = new ServiceTracker<ObjectProvider1<AnnotationMember>, ObjectProvider1<AnnotationMember>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<AnnotationMember> p = providerTracker.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				AnnotationMember config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				try {
					assertThat(config.error()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.errors()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}

			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testClassMember130() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.ClassMemberComponent))");
			ServiceTracker<ObjectProvider1<ClassMember>, ObjectProvider1<ClassMember>> providerTracker = new ServiceTracker<ObjectProvider1<ClassMember>, ObjectProvider1<ClassMember>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<ClassMember> p = providerTracker.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				ClassMember config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				Class<?> single = config.single();
				assertThat(single).as("property has wrong value").isSameAs(ClassMember.class);
				Class<?>[] multiple = config.multiple();
				assertThat(multiple).as("array null").isNotNull();
				assertThat(multiple.length).as("array wrong length").isEqualTo(2);
				assertThat(multiple[0]).as("property has wrong value").isSameAs(p.getClass());
				assertThat(multiple[1]).as("property has wrong value").isSameAs(ClassMember.class);
			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testEnumMember130() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ObjectProvider1.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb26.EnumMemberComponent))");
			ServiceTracker<ObjectProvider1<EnumMember>, ObjectProvider1<EnumMember>> providerTracker = new ServiceTracker<ObjectProvider1<EnumMember>, ObjectProvider1<EnumMember>>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ObjectProvider1<EnumMember> p = providerTracker.waitForService(SLEEP * 3);
				assertThat(p).as("missing provider").isNotNull();
				EnumMember config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				TestEnum single = config.single();
				assertThat(single).as("property has wrong value").isSameAs(TestEnum.ITEM1);
				TestEnum[] multiple = config.multiple();
				assertThat(multiple).as("array null").isNotNull();
				assertThat(multiple.length).as("array wrong length").isEqualTo(2);
				assertThat(multiple[0]).as("property has wrong value").isSameAs(TestEnum.AnotherItem);
				assertThat(multiple[1]).as("property has wrong value").isSameAs(TestEnum.ITEM1);
			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

	@Test
	public void testCoercion130() throws Exception {
		Bundle tb26 = installer.installBundle("tb26.jar", false);
		assertThat(tb26).as("tb26 failed to install").isNotNull();
		try {
			tb26.start();

			Filter providerFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ComponentFactory.class.getName() + ")(" + ComponentConstants.COMPONENT_FACTORY
					+ "=org.osgi.test.cases.component.tb26.CoercionComponent))");
			ServiceTracker<ComponentFactory<ObjectProvider1<Coercion>>,ComponentFactory<ObjectProvider1<Coercion>>> providerTracker = new ServiceTracker<>(
					context, providerFilter, null);
			try {
				providerTracker.open();
				ComponentFactory<ObjectProvider1<Coercion>> f = providerTracker
						.waitForService(SLEEP * 3);
				assertThat(f).as("missing factory").isNotNull();
				Dictionary<String, Object> props = new Hashtable<String, Object>();
				props.put("stringCollection", Arrays.asList("list", "second"));
				props.put("booleanCollection", Arrays.asList(Boolean.TRUE, Boolean.TRUE));
				props.put("charCollection", Arrays.asList(Character.valueOf('@'), Character.valueOf('\u0000')));
				props.put("byteCollection", Arrays.asList(Byte.valueOf((byte) 64), Byte.valueOf((byte) 0)));
				props.put("shortCollection", Arrays.asList(Short.valueOf((short) 64), Short.valueOf((short) 0)));
				props.put("intCollection", Arrays.asList(Integer.valueOf(64), Integer.valueOf(0)));
				props.put("longCollection", Arrays.asList(Long.valueOf(64), Long.valueOf(0)));
				props.put("floatCollection", Arrays.asList(Float.valueOf(64.3F), Float.valueOf(0.3F)));
				props.put("doubleCollection", Arrays.asList(Double.valueOf(64.3D), Double.valueOf(0.3D)));
				props.put("classCollection",
						Arrays.asList("org.osgi.test.cases.component.types.Coercion",
								"org.osgi.test.cases.component.tb26.CoercionComponent"));
				props.put("enumCollection", Arrays.asList(TestEnum.ITEM1.name(),
						TestEnum.AnotherItem.name()));
				props.put("int$Collection", Arrays.asList(Integer.valueOf(64), Integer.valueOf(0)));
				ObjectProvider1<Coercion> p = f.newInstance(props).getInstance();
				assertThat(p).as("missing provider").isNotNull();
				Coercion config = p.get1();
				assertThat(config).as("missing config").isNotNull();

				assertThat(config.stringString()).as("property has wrong value").isEqualTo("xml/stringString");
				assertThat(config.stringBoolean()).as("property has wrong value").isEqualTo("true");
				assertThat(config.stringCharacter()).as("property has wrong value").isEqualTo("@");
				assertThat(config.stringByte()).as("property has wrong value").isEqualTo("2");
				assertThat(config.stringShort()).as("property has wrong value").isEqualTo("1024");
				assertThat(config.stringInteger()).as("property has wrong value").isEqualTo("123456");
				assertThat(config.stringLong()).as("property has wrong value").isEqualTo("9876543210");
				assertThat(config.stringFloat()).as("property has wrong value").isEqualTo("3.14");
				assertThat(config.stringDouble()).as("property has wrong value").isEqualTo("2.1");
				assertThat(config.stringCollection()).as("property has wrong value").isEqualTo("list");
				assertThat(config.stringArray()).as("property has wrong value").isEqualTo("true");
				assertThat(config.stringNone()).as("property has wrong value").isNull();

				assertThat(config.booleanString()).as("property has wrong value").isTrue();
				assertThat(config.booleanBoolean()).as("property has wrong value").isTrue();
				assertThat(config.booleanCharacter()).as("property has wrong value").isFalse();
				assertThat(config.booleanByte()).as("property has wrong value").isTrue();
				assertThat(config.booleanShort()).as("property has wrong value").isTrue();
				assertThat(config.booleanInteger()).as("property has wrong value").isTrue();
				assertThat(config.booleanLong()).as("property has wrong value").isTrue();
				assertThat(config.booleanFloat()).as("property has wrong value").isTrue();
				assertThat(config.booleanDouble()).as("property has wrong value").isFalse();
				assertThat(config.booleanCollection()).as("property has wrong value").isTrue();
				assertThat(config.booleanArray()).as("property has wrong value").isTrue();
				assertThat(config.booleanNone()).as("property has wrong value").isFalse();

				assertThat(config.charString()).as("property has wrong value").isEqualTo('6');
				assertThat(config.charStringEmpty()).as("property has wrong value").isEqualTo((char) 0);
				assertThat(config.charBooleanTrue()).as("property has wrong value").isEqualTo((char) 1);
				assertThat(config.charBooleanFalse()).as("property has wrong value").isEqualTo((char) 0);
				assertThat(config.charCharacter()).as("property has wrong value").isEqualTo('@');
				assertThat(config.charByte()).as("property has wrong value").isEqualTo((char) 2);
				assertThat(config.charShort()).as("property has wrong value").isEqualTo((char) 1034);
				assertThat(config.charInteger()).as("property has wrong value").isEqualTo((char) 123456);
				assertThat(config.charLong()).as("property has wrong value").isEqualTo((char) 9876543210L);
				assertThat(config.charFloat()).as("property has wrong value").isEqualTo((char) 3.14F);
				assertThat(config.charDouble()).as("property has wrong value").isEqualTo((char) 2.1D);
				assertThat(config.charCollection()).as("property has wrong value").isEqualTo('@');
				assertThat(config.charArray()).as("property has wrong value").isEqualTo((char) 1);
				assertThat(config.charNone()).as("property has wrong value").isEqualTo((char) 0);

				assertThat(config.byteString()).as("property has wrong value").isEqualTo((byte) 64);
				assertThat(config.byteBooleanTrue()).as("property has wrong value").isOne();
				assertThat(config.byteBooleanFalse()).as("property has wrong value").isZero();
				assertThat(config.byteCharacter()).as("property has wrong value").isEqualTo((byte) 64);
				assertThat(config.byteByte()).as("property has wrong value").isEqualTo((byte) 2);
				assertThat(config.byteShort()).as("property has wrong value").isEqualTo((byte) 1034);
				assertThat(config.byteInteger()).as("property has wrong value").isEqualTo((byte) 123456);
				assertThat(config.byteLong()).as("property has wrong value").isEqualTo((byte) 9876543210L);
				assertThat(config.byteFloat()).as("property has wrong value").isEqualTo((byte) 3.14F);
				assertThat(config.byteDouble()).as("property has wrong value").isEqualTo((byte) 2.1D);
				assertThat(config.byteCollection()).as("property has wrong value").isEqualTo((byte) 64);
				assertThat(config.byteArray()).as("property has wrong value").isOne();
				assertThat(config.byteNone()).as("property has wrong value").isZero();

				assertThat(config.shortString()).as("property has wrong value").isEqualTo((short) 64);
				assertThat(config.shortBooleanTrue()).as("property has wrong value").isOne();
				assertThat(config.shortBooleanFalse()).as("property has wrong value").isZero();
				assertThat(config.shortCharacter()).as("property has wrong value").isEqualTo((short) 64);
				assertThat(config.shortByte()).as("property has wrong value").isEqualTo((short) 2);
				assertThat(config.shortShort()).as("property has wrong value").isEqualTo((short) 1034);
				assertThat(config.shortInteger()).as("property has wrong value").isEqualTo((short) 123456);
				assertThat(config.shortLong()).as("property has wrong value").isEqualTo((short) 9876543210L);
				assertThat(config.shortFloat()).as("property has wrong value").isEqualTo((short) 3.14F);
				assertThat(config.shortDouble()).as("property has wrong value").isEqualTo((short) 2.1D);
				assertThat(config.shortCollection()).as("property has wrong value").isEqualTo((short) 64);
				assertThat(config.shortArray()).as("property has wrong value").isOne();
				assertThat(config.shortNone()).as("property has wrong value").isZero();

				assertThat(config.intString()).as("property has wrong value").isEqualTo(64);
				assertThat(config.intBooleanTrue()).as("property has wrong value").isOne();
				assertThat(config.intBooleanFalse()).as("property has wrong value").isZero();
				assertThat(config.intCharacter()).as("property has wrong value").isEqualTo(64);
				assertThat(config.intByte()).as("property has wrong value").isEqualTo(2);
				assertThat(config.intShort()).as("property has wrong value").isEqualTo(1034);
				assertThat(config.intInteger()).as("property has wrong value").isEqualTo(123456);
				assertThat(config.intLong()).as("property has wrong value").isEqualTo((int) 9876543210L);
				assertThat(config.intFloat()).as("property has wrong value").isEqualTo((int) 3.14F);
				assertThat(config.intDouble()).as("property has wrong value").isEqualTo((int) 2.1D);
				assertThat(config.intCollection()).as("property has wrong value").isEqualTo(64);
				assertThat(config.intArray()).as("property has wrong value").isOne();
				assertThat(config.intNone()).as("property has wrong value").isZero();

				assertThat(config.longString()).as("property has wrong value").isEqualTo(64);
				assertThat(config.longBooleanTrue()).as("property has wrong value").isOne();
				assertThat(config.longBooleanFalse()).as("property has wrong value").isZero();
				assertThat(config.longCharacter()).as("property has wrong value").isEqualTo(64);
				assertThat(config.longByte()).as("property has wrong value").isEqualTo(2);
				assertThat(config.longShort()).as("property has wrong value").isEqualTo(1034);
				assertThat(config.longInteger()).as("property has wrong value").isEqualTo(123456);
				assertThat(config.longLong()).as("property has wrong value").isEqualTo(9876543210L);
				assertThat(config.longFloat()).as("property has wrong value").isEqualTo((long) 3.14F);
				assertThat(config.longDouble()).as("property has wrong value").isEqualTo((long) 2.1D);
				assertThat(config.longCollection()).as("property has wrong value").isEqualTo(64);
				assertThat(config.longArray()).as("property has wrong value").isOne();
				assertThat(config.longNone()).as("property has wrong value").isZero();

				final float deltaFloat = 0x1.0p-126f;
				assertThat(config.floatString()).as("property has wrong value").isCloseTo(64.3F, within(deltaFloat));
				assertThat(config.floatBooleanTrue()).as("property has wrong value").isCloseTo(1, within(deltaFloat));
				assertThat(config.floatBooleanFalse()).as("property has wrong value").isCloseTo(0, within(deltaFloat));
				assertThat(config.floatCharacter()).as("property has wrong value").isCloseTo(64, within(deltaFloat));
				assertThat(config.floatByte()).as("property has wrong value").isCloseTo(2, within(deltaFloat));
				assertThat(config.floatShort()).as("property has wrong value").isCloseTo(1034, within(deltaFloat));
				assertThat(config.floatInteger()).as("property has wrong value").isCloseTo(123456, within(deltaFloat));
				assertThat(config.floatLong()).as("property has wrong value").isCloseTo(9876543210L, within(deltaFloat));
				assertThat(config.floatFloat()).as("property has wrong value").isCloseTo(3.14F, within(deltaFloat));
				assertThat(config.floatDouble()).as("property has wrong value").isCloseTo((float) 2.1D, within(deltaFloat));
				assertThat(config.floatCollection()).as("property has wrong value").isCloseTo(64.3F, within(deltaFloat));
				assertThat(config.floatArray()).as("property has wrong value").isCloseTo(1F, within(deltaFloat));
				assertThat(config.floatNone()).as("property has wrong value").isCloseTo(0F, within(deltaFloat));

				final double deltaDouble = 0x1.0p-1022;
				assertThat(config.doubleString()).as("property has wrong value").isCloseTo(64.3D, within(deltaDouble));
				assertThat(config.doubleBooleanTrue()).as("property has wrong value").isCloseTo(1, within(deltaDouble));
				assertThat(config.doubleBooleanFalse()).as("property has wrong value").isCloseTo(0, within(deltaDouble));
				assertThat(config.doubleCharacter()).as("property has wrong value").isCloseTo(64, within(deltaDouble));
				assertThat(config.doubleByte()).as("property has wrong value").isCloseTo(2, within(deltaDouble));
				assertThat(config.doubleShort()).as("property has wrong value").isCloseTo(1034, within(deltaDouble));
				assertThat(config.doubleInteger()).as("property has wrong value").isCloseTo(123456, within(deltaDouble));
				assertThat(config.doubleLong()).as("property has wrong value").isCloseTo(9876543210L, within(deltaDouble));
				assertThat(config.doubleFloat()).as("property has wrong value").isCloseTo(3.14F, within(deltaDouble));
				assertThat(config.doubleDouble()).as("property has wrong value").isCloseTo(2.1D, within(deltaDouble));
				assertThat(config.doubleCollection()).as("property has wrong value").isCloseTo(64.3D, within(deltaDouble));
				assertThat(config.doubleArray()).as("property has wrong value").isCloseTo(1D, within(deltaDouble));
				assertThat(config.doubleNone()).as("property has wrong value").isCloseTo(0D, within(deltaDouble));

				try {
					assertThat(config.classBoolean()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classCharacter()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classByte()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classShort()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classInteger()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classLong()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classFloat()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.classDouble()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				assertThat(config.classCollection()).as("property has wrong value").isSameAs(Coercion.class);
				assertThat(config.classArray()).as("property has wrong value").isSameAs(p.getClass());
				assertThat(config.classNone()).as("property has wrong value").isNull();

				try {
					assertThat(config.enumBoolean()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumCharacter()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumByte()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumShort()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumInteger()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumLong()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumFloat()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				try {
					assertThat(config.enumDouble()).as("did not throw Exception").isEqualTo(ComponentException.class);
				} catch (ComponentException e) {
					// expected
				}
				assertThat(config.enumCollection()).as("property has wrong value").isSameAs(TestEnum.ITEM1);
				assertThat(config.enumArray()).as("property has wrong value").isSameAs(TestEnum.AnotherItem);
				assertThat(config.enumNone()).as("property has wrong value").isNull();

				assertThat(config.string$$String()).as("array null").isNotNull();
				assertThat(config.string$$String().length).as("array wrong length").isOne();
				assertThat(config.string$$String()[0]).as("property has wrong value").isEqualTo("xml/stringString");
				assertThat(config.boolean$$Boolean()).as("array null").isNotNull();
				assertThat(config.boolean$$Boolean().length).as("array wrong length").isOne();
				assertThat(config.boolean$$Boolean()[0]).as("property has wrong value").isTrue();
				assertThat(config.char$$Character()).as("array null").isNotNull();
				assertThat(config.char$$Character().length).as("array wrong length").isOne();
				assertThat(config.char$$Character()[0]).as("property has wrong value").isEqualTo('@');
				assertThat(config.byte$$Byte()).as("array null").isNotNull();
				assertThat(config.byte$$Byte().length).as("array wrong length").isOne();
				assertThat(config.byte$$Byte()[0]).as("property has wrong value").isEqualTo((byte) 2);
				assertThat(config.short$$Short()).as("array null").isNotNull();
				assertThat(config.short$$Short().length).as("array wrong length").isOne();
				assertThat(config.short$$Short()[0]).as("property has wrong value").isEqualTo((short) 1034);
				assertThat(config.int$$Integer()).as("array null").isNotNull();
				assertThat(config.int$$Integer().length).as("array wrong length").isOne();
				assertThat(config.int$$Integer()[0]).as("property has wrong value").isEqualTo(123456);
				assertThat(config.long$$Long()).as("array null").isNotNull();
				assertThat(config.long$$Long().length).as("array wrong length").isOne();
				assertThat(config.long$$Long()[0]).as("property has wrong value").isEqualTo(9876543210L);
				assertThat(config.float$$Float()).as("array null").isNotNull();
				assertThat(config.float$$Float().length).as("array wrong length").isOne();
				assertThat(config.float$$Float()[0]).as("property has wrong value").isCloseTo(3.14F, within(deltaFloat));
				assertThat(config.double$$Double()).as("array null").isNotNull();
				assertThat(config.double$$Double().length).as("array wrong length").isOne();
				assertThat(config.double$$Double()[0]).as("property has wrong value").isCloseTo(2.1D, within(deltaDouble));
				assertThat(config.int$$Collection()).as("array null").isNotNull();
				assertThat(config.int$$Collection().length).as("array wrong length").isEqualTo(2);
				assertThat(config.int$$Collection()[0]).as("property has wrong value").isEqualTo(64);
				assertThat(config.int$$Collection()[1]).as("property has wrong value").isZero();
				assertThat(config.long$$Array()).as("array null").isNotNull();
				assertThat(config.long$$Array().length).as("array wrong length").isEqualTo(2);
				assertThat(config.long$$Array()[0]).as("property has wrong value").isEqualTo(9876543210L);
				assertThat(config.long$$Array()[1]).as("property has wrong value").isEqualTo(123456L);
				// this changed in 1.4 (R7) - while it has to be null in 1.3 it must be an empty array in 1.4:
				assertThat(config.string$$None()).as("array null").isNotNull();
				assertThat(config.string$$None().length).as("array wrong length").isZero();

			} finally {
				providerTracker.close();
			}
		} finally {
			tb26.uninstall();
		}
	}

}
