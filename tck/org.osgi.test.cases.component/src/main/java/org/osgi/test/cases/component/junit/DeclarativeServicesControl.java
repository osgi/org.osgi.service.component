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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.osgi.test.assertj.bundle.BundleAssert.assertThat;
import static org.osgi.test.cases.component.junit.DTOUtil.newBundleDTO;
import static org.osgi.test.cases.component.junit.DTOUtil.newComponentDescriptionDTO;
import static org.osgi.test.cases.component.junit.DTOUtil.newReferenceDTO;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.namespace.extender.ExtenderNamespace;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.resource.Namespace;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.ReferenceDTO;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;
import org.osgi.test.cases.component.service.BaseService;
import org.osgi.test.cases.component.service.ComponentContextExposer;
import org.osgi.test.cases.component.service.ComponentEnabler;
import org.osgi.test.cases.component.service.DSTestConstants;
import org.osgi.test.cases.component.service.MultipleFieldTestService;
import org.osgi.test.cases.component.service.ScalarFieldTestService;
import org.osgi.test.cases.component.service.ServiceProvider;
import org.osgi.test.cases.component.service.ServiceReceiver;
import org.osgi.test.cases.component.service.TBCService;
import org.osgi.test.cases.component.service.TestIdentitySet;
import org.osgi.test.cases.component.service.TestList;
import org.osgi.test.cases.component.service.TestObject;
import org.osgi.test.cases.component.service.TestSet;
import org.osgi.test.cases.component.tb13.ModifyRegistrator;
import org.osgi.test.cases.component.tb13a.ModifyRegistrator2;
import org.osgi.test.cases.component.tb6.ActDeactComponent;
import org.osgi.test.common.annotation.InjectAwaitCalm;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.await.AwaitCalm;
import org.osgi.test.common.await.AwaitCalmTimeoutException;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This is the bundle initially installed and started by the TestCase. It
 * performs the test methods of the declarative services test case.
 *
 * @author $Id$
 */
public class DeclarativeServicesControl
		implements LogListener {
	static final long		SETTLE_QUIET_MILLIS	= 300;

	@InjectAwaitCalm
	AwaitCalm				awaitCalm;


	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;

	private static final String	TEST_CASE_ROOT		= "org.osgi.test.cases.component";
	private static final String	LOOKUP_CLASS		= TEST_CASE_ROOT
															+ ".tb2.ServiceConsumerLookup";
	private static final String	DYN_CLASS			= TEST_CASE_ROOT
															+ ".tb2.DynService";
	private static final String	EVENT_CLASS			= TEST_CASE_ROOT
															+ ".tb3.ServiceConsumerEvent";
	private static final String	NAMED_CLASS			= TEST_CASE_ROOT
															+ ".tb4.NamedService";
	private static final String	COMP_OPTIONAL_100	= TEST_CASE_ROOT
															+ ".tb5.optionalNS100";
	private static final String	COMP_OPTIONAL_110	= TEST_CASE_ROOT
															+ ".tb5.optionalNS110";
	private static final String	COMP_REQUIRE_100	= TEST_CASE_ROOT
															+ ".tb5.requireNS100";
	private static final String	COMP_REQUIRE_110	= TEST_CASE_ROOT
															+ ".tb5.requireNS110";
	private static final String	COMP_IGNORE_100		= TEST_CASE_ROOT
															+ ".tb5.ignoreNS100";
	private static final String	COMP_IGNORE_110		= TEST_CASE_ROOT
															+ ".tb5.ignoreNS110";
	private static final String	COMP_NOTSET_100		= TEST_CASE_ROOT
															+ ".tb5.notsetNS100";
	private static final String	COMP_NOTSET_110		= TEST_CASE_ROOT
															+ ".tb5.notsetNS110";
	private static final String	MOD_NOTSET_NS100	= TEST_CASE_ROOT
															+ ".tb13.notsetNS100";
	private static final String	MOD_NOTSET_NS110	= TEST_CASE_ROOT
															+ ".tb13.notsetNS110";
	private static final String	MOD_NOARGS_NS100	= TEST_CASE_ROOT
															+ ".tb13.NoArgs100";
	private static final String	MOD_NOARGS_NS110	= TEST_CASE_ROOT
															+ ".tb13.NoArgs110";
	private static final String	MOD_CC_NS100		= TEST_CASE_ROOT
															+ ".tb13.CcNS100";
	private static final String	MOD_CC_NS110		= TEST_CASE_ROOT
															+ ".tb13.CcNS110";
	private static final String	MOD_BC_NS100		= TEST_CASE_ROOT
															+ ".tb13.BcNS100";
	private static final String	MOD_BC_NS110		= TEST_CASE_ROOT
															+ ".tb13.BcNS110";
	private static final String	MOD_MAP_NS100		= TEST_CASE_ROOT
															+ ".tb13.MapNS100";
	private static final String	MOD_MAP_NS110		= TEST_CASE_ROOT
															+ ".tb13.MapNS110";
	private static final String	MOD_CC_BC_MAP_NS100	= TEST_CASE_ROOT
															+ ".tb13.CcBcMapNS100";
	private static final String	MOD_CC_BC_MAP_NS110	= TEST_CASE_ROOT
															+ ".tb13.CcBcMapNS110";
	private static final String	MOD_NOT_EXIST_NS110	= TEST_CASE_ROOT
															+ ".tb13.NotExistNS110";
	private static final String	MOD_THROW_EX_NS110	= TEST_CASE_ROOT
															+ ".tb13.ThrowExNS110";

	private static int			SLEEP			= 1000;
	private boolean synchronousBuild = false;

	private Bundle				tb1, tb2, tb3;

	private ServiceTracker<ServiceProvider,ServiceProvider>					trackerProvider;
	private ServiceTracker<Object,Object>										trackerConsumerLookup;
	private ServiceTracker<Object,Object>										trackerDyn;
	private ServiceTracker<Object,Object>										trackerConsumerEvent;
	private ServiceTracker<Object,Object>										trackerNamedService;
	private ServiceTracker<ComponentFactory<Object>,ComponentFactory<Object>>	trackerNamedServiceFactory;
	private ServiceTracker<ConfigurationAdmin, ConfigurationAdmin>	trackerCM;
	private ServiceTracker<BaseService, BaseService>	trackerBaseService;
	private ServiceTracker<ServiceComponentRuntime, ServiceComponentRuntime>	scrTracker;

	@BeforeEach
	public void setUp() throws Exception {
		String sleepTimeString = AccessController.doPrivileged((PrivilegedAction<String>) () -> context.getProperty("osgi.tc.component.sleeptime"));
		int sleepTime = SLEEP;
		if (sleepTimeString != null) {
			try {
				sleepTime = Integer.parseInt(sleepTimeString);
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out
						.println("Error while parsing sleep value! The default one will be used : "
								+ SLEEP);
			}
			if (sleepTime < 100) {
				System.out.println("The sleep value is too low : " + sleepTime
						+ " ! The default one will be used : " + SLEEP);
			}
			else {
				SLEEP = sleepTime;
			}
		}

		BundleContext bc = context;
		trackerCM = new ServiceTracker<ConfigurationAdmin, ConfigurationAdmin>(bc, ConfigurationAdmin.class,
				null);
		trackerCM.open();
	    // clear component configurations
 	    clearConfigurations();

		scrTracker = new ServiceTracker<ServiceComponentRuntime, ServiceComponentRuntime>(bc,
				ServiceComponentRuntime.class, null);
		scrTracker.open();

		// install test cases
		tb1 = installer.installBundle(context.getBundle().getEntry("tb1.jar"), false);
		tb2 = installer.installBundle(context.getBundle().getEntry("tb2.jar"), false);
		tb3 = installer.installBundle(context.getBundle().getEntry("tb3.jar"), false);

		// start them
		tb1.start();
		tb2.start();
		tb3.start();

		// init trackers
		trackerProvider = new ServiceTracker<>(bc, ServiceProvider.class, null);
		trackerConsumerLookup = new ServiceTracker<>(bc, LOOKUP_CLASS, null);
		trackerDyn = new ServiceTracker<>(bc, DYN_CLASS, null);
		trackerConsumerEvent = new ServiceTracker<>(bc, EVENT_CLASS, null);
		trackerNamedService = new ServiceTracker<>(bc, NAMED_CLASS, null);
		Filter filter = bc.createFilter("(&("
				+ ComponentConstants.COMPONENT_FACTORY + '=' + NAMED_CLASS
				+ ")(" + Constants.OBJECTCLASS + '='
				+ ComponentFactory.class.getName() + "))");
		trackerNamedServiceFactory = new ServiceTracker<>(bc, filter, null);
		trackerBaseService = new ServiceTracker<BaseService, BaseService>(bc, BaseService.class, null);

		// start listening
		trackerProvider.open(true);
		trackerConsumerLookup.open();
		trackerDyn.open();
		trackerConsumerEvent.open();
		trackerNamedService.open();
		trackerNamedServiceFactory.open();
		trackerBaseService.open();

		settle(SLEEP);
	}

	/**
	 * Clean up after a run. Notice that during debugging many times the
	 * unprepare is never reached.
	 *
	 * @throws Exception is the bundles are not installed
	 */
	@AfterEach
	public void tearDown() throws Exception {
		tb1.uninstall();
		tb2.uninstall();
		tb3.uninstall();

		trackerProvider.close();
		trackerConsumerLookup.close();
		trackerDyn.close();
		trackerConsumerEvent.close();
		trackerNamedService.close();
		trackerNamedServiceFactory.close();
		trackerBaseService.close();

		clearConfigurations();
		trackerCM.close();
		scrTracker.close();
	}

	/**
	 * This methods takes care of the configurations related to this test
	 *
	 * @throws IOException
	 * @throws InvalidSyntaxException
	 * @throws InterruptedException
	 */
	private void clearConfigurations() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		// clean configurations from previous tests
		// clean factory configs for named service
		clearConfiguration(cm, "(service.factoryPid=" + TEST_CASE_ROOT + "*)");
		// clean configs for named service
		clearConfiguration(cm, "(service.pid=" + TEST_CASE_ROOT + "*)");

		settle(SLEEP * 2);
	}

	private void clearConfiguration(ConfigurationAdmin cm, String filter)
			throws Exception {
		Configuration[] configs = cm.listConfigurations(filter);
		for (int i = 0; configs != null && i < configs.length; i++) {
			Configuration configuration = configs[i];
			configuration.delete();
		}
	}

	/**
	 * Tests registering / unregistering of the component bundles and their
	 * provided services.
	 *
	 * @throws Exception can be thrown at any time
	 */
	@Test
	public void testRegistration() throws Exception {
		TBCService serviceProvider, serviceConsumerLookup, serviceConsumerEvent;

		serviceProvider = trackerProvider.getService();
		assertThat(tb1).as("ServiceProvider bundle should be in active state").isInState(Bundle.ACTIVE);
		assertThat(serviceProvider).as("ServiceProvider service should be registered").isNotNull();

		serviceConsumerLookup = (TBCService) trackerConsumerLookup.getService();
		assertThat(tb2).as("ServiceConsumerLookup bundle should be in active state").isInState(Bundle.ACTIVE);
		assertThat(serviceConsumerLookup).as("ServiceConsumerLookup service should be registered").isNotNull();

		serviceConsumerEvent = (TBCService) trackerConsumerEvent.getService();
		assertThat(tb3).as("ServiceConsumerEvent bundle should be in active state").isInState(Bundle.ACTIVE);
		assertThat(serviceConsumerEvent).as("ServiceConsumerEvent service should be registered").isNotNull();

		tb1.uninstall();
		settle(SLEEP);
		serviceProvider = trackerProvider.getService();
		assertThat(serviceProvider).as("ServiceProvider service should not be registered").isNull();

		tb1 = installer.installBundle(context.getBundle().getEntry("tb1.jar"), false);
		settle(SLEEP);
		serviceProvider = trackerProvider.getService();
		assertThat((tb1
				.getState() & (Bundle.RESOLVED | Bundle.INSTALLED)) != 0).as("ServiceProvider bundle should be in resolved state").isTrue();
		assertThat(serviceProvider).as("ServiceProvider service should not be registered").isNull();

		tb1.start();
		settle(SLEEP);
		serviceProvider = trackerProvider.getService();
		assertThat(tb1).as("ServiceProvider bundle should be in active state").isInState(Bundle.ACTIVE);
		assertThat(serviceProvider).as("ServiceProvider service should be registered").isNotNull();
	}

	/**
	 * Tests getting of the provided service from the first component bundle
	 * directly.
	 */
	@Test
	public void testGetServiceDirect() {
		TBCService serviceProvider = trackerProvider.getService();
		TestObject testObject = serviceProvider.getTestObject();
		assertThat(testObject.getClass()).as("The return value should be an instance of TestObject").isEqualTo(TestObject.class);
	}

	/**
	 * Tests getting of the provided service of the second component bundle
	 * through the lookup method.
	 */
	@Test
	public void testGetServiceLookup() {
		TBCService serviceConsumerLookup = (TBCService) trackerConsumerLookup
				.getService();
		TestObject testObject = serviceConsumerLookup.getTestObject();
		assertThat(testObject.getClass()).as("The return value should be an instance of TestObject").isEqualTo(TestObject.class);
	}

	/**
	 * Tests getting of the provided service of the third component bundle
	 * through the event method.
	 */
	@Test
	public void testGetServiceEvent() {
		TBCService serviceConsumerEvent = (TBCService) trackerConsumerEvent
				.getService();
		TestObject testObject = serviceConsumerEvent.getTestObject();
		assertThat(testObject.getClass()).as("The return value should be an instance of TestObject").isEqualTo(TestObject.class);
	}

	/**
	 * Tests getting of the component properties from the second component
	 * bundle set through property and properties elements in XML.
	 */
	@Test
	public void testGetProperties() {
		TBCService serviceConsumerLookup = (TBCService) trackerConsumerLookup
				.getService();
		Dictionary<String,Object> properties = serviceConsumerLookup
				.getProperties();
		assertThat(properties).as("Properties should not be empty").isNotNull();
		assertThat(properties
				.size() >= 2).as("The size of properties should be at least 2").isTrue();
		assertThat(properties.get("test.property.int")).as("Value of test.property.int should be equal to '123'").isEqualTo("123");
		String[] array = (String[]) properties.get("test.property.string");
		assertThat(array.length).as("The size of test.property.string array should be 3").isEqualTo(3);
		assertThat(array).as("test.property.string array should be").isEqualTo(new String[]{"Value 1", "Value 2", "Value 3"});
	}

	/**
	 * This test check if the SCR will unregister all components when it is
	 * stopped!
	 *
	 * @throws Exception
	 */
	@Test
	public void testStartStopSCR() throws Exception {
		Bundle scr = getSCRBundle();
		ServiceReference< ? > refs[];
		BundleContext bc = context;
		String filter = "(" + ComponentConstants.COMPONENT_NAME + "=*)";

		// preserve the count of the registered components
		// after SRC is started again, the same number of components
		// must be registered
		refs = bc.getServiceReferences((String) null, filter);
		int count = (refs == null) ? 0 : refs.length;

		scr.stop();
		settle(SLEEP * 2);

		try {
			refs = bc.getServiceReferences((String) null, filter);
			assertThat(refs).as("The Service Component Runtime must stop all services if SCR is stopped").isNull();
		}
		finally {
			// make sure that after SCR is being started it will activate
			// the components which bundles are active
			scr.start();
			settle(SLEEP * 2);
		}

		refs = bc.getServiceReferences((String) null, filter);
		assertThat((refs == null) ? 0 : refs.length).as("The Service Component Runtime must start all components that are installed prior it").isEqualTo(count);
	}

	@Test
	public void testSCRExtenderCapability() throws Exception {
		Bundle scr = getSCRBundle();
		BundleWiring wiring = scr.adapt(BundleWiring.class);
		assertThat(wiring).as("No BundleWiring available for SCR bundle").isNotNull();
		boolean found = false;
		List<BundleCapability> extenders = wiring
				.getCapabilities(ExtenderNamespace.EXTENDER_NAMESPACE);
		for (BundleCapability extender : extenders) {
			if (ComponentConstants.COMPONENT_CAPABILITY_NAME
					.equals(extender.getAttributes()
							.get(ExtenderNamespace.EXTENDER_NAMESPACE))) {
				found = true;
				assertThat(extender.getAttributes()
								.get(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE)).as("osgi.extender capability version wrong").isEqualTo(new Version(
								ComponentConstants.COMPONENT_SPECIFICATION_VERSION));
				String uses = extender.getDirectives().get(
						Namespace.CAPABILITY_USES_DIRECTIVE);
				assertThat(uses).as("osgi.extender capability uses directive missing").isNotNull();
				boolean usefound = false;
				for (String use : uses.split("\\s*,\\s*")) {
					if ("org.osgi.service.component".equals(use)) {
						usefound = true;
						break;
					}
				}
				assertThat(usefound).as("osgi.extender capability missing package in uses").isTrue();
				break;
			}
		}
		assertThat(found).as("missing osgi.extender capability for osgi.component").isTrue();
	}

	@Test
	public void testSCRServiceCapability() throws Exception {
		Bundle scr = getSCRBundle();
		BundleWiring wiring = scr.adapt(BundleWiring.class);
		assertThat(wiring).as("No BundleWiring available for SCR bundle").isNotNull();
		boolean found = false;
		List<BundleCapability> services = wiring
				.getCapabilities(ServiceNamespace.SERVICE_NAMESPACE);
		for (BundleCapability service : services) {
			@SuppressWarnings("unchecked")
			List<String> objectClass = (List<String>) service.getAttributes()
					.get(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE);
			if (objectClass.contains(ServiceComponentRuntime.class.getName())) {
				found = true;
				String uses = service.getDirectives().get(
						Namespace.CAPABILITY_USES_DIRECTIVE);
				assertThat(uses).as("osgi.service capability uses directive missing").isNotNull();
				boolean usefound = false;
				for (String use : uses.split("\\s*,\\s*")) {
					if ("org.osgi.service.component.runtime".equals(use)) {
						usefound = true;
						break;
					}
				}
				assertThat(usefound).as("osgi.service capability missing package in uses").isTrue();
				break;
			}
		}
		assertThat(found).as("missing osgi.service capability for "
				+ ServiceComponentRuntime.class.getName()).isTrue();
	}

	/**
	 * This method will check the correct behaviour of component factories
	 * registrations. It will perform the following checks:
	 *
	 * <code>
	 * + install a component factory bundle
	 *  - make sure that the component factory is registered as service
	 * + create a new component (using a component factory)
	 *  - make sure that the component is registered as service
	 *  - make sure that service is registered with correct properties
	 *  - make sure that the service receives the correct properties in the
	 *    activate method.
	 * + stop the component factory bundle
	 *  - make sure that all components created by this factory are also disposed
	 * </code>
	 *
	 * @throws Exception
	 */
	@Test
	public void testComponentFactory() throws Exception {
		Bundle bundle = installer.installBundle(context.getBundle().getEntry("tb4.jar"), false);
		bundle.start();
		settle(SLEEP);

		Hashtable<String,Object> props;
		int oldCount = trackerNamedService.getTrackingCount();
		ComponentFactory<Object> factory = trackerNamedServiceFactory
				.getService();

		// create the first service
		props = new Hashtable<>();
		props.put("name", "hello");
		factory.newInstance(props);

		assertThat(trackerNamedService.getTrackingCount()).as("The instances tracking count should be increased by one").isEqualTo(oldCount + 1);
		boolean serviceFound = false;
		// find the service, the reference must have the properties set
		// correctly
		ServiceReference<Object> refs[] = trackerNamedService
				.getServiceReferences();
		for (int i = 0; refs != null && i < refs.length; i++) {
			ServiceReference<Object> current = refs[i];
			String name = (String) current.getProperty("name");
			if ("hello".equals(name)) {
				serviceFound = true;
				Object service = trackerNamedService.getService(current);
				assertThat(service).as("The service must have the same name").hasToString("hello");
				break;
			}
		}
		// fail if the service was not found
		assertThat(serviceFound).as("A service should be registered").isTrue();

		// create the second service
		props = new Hashtable<>();
		props.put("name", "world");
		ComponentInstance<Object> worldInstance = factory.newInstance(props);
		settle(SLEEP);

		// make sure that the new service is registered
		assertThat(trackerNamedService.getTrackingCount()).as("The instances tracking count should be increased by two").isEqualTo(oldCount + 2);

		worldInstance.dispose();
		settle(SLEEP);
		assertThat(trackerNamedService.getServices().length).as("The service should be unregistered if the instance is disposed").isEqualTo(oldCount + 1);

		// stop the bundle in order to check if some services remained
		// registered
		bundle.uninstall();
		settle(SLEEP);

		assertThat(trackerNamedServiceFactory.getService()).as("The component factory must be unregistered").isNull();
		assertThat(trackerNamedService.getService()).as("All component instances, created by the factory should be unregistered").isNull();
	}

	boolean	errorLog	= false;

	@SuppressWarnings("deprecation")
	public void logged(LogEntry e) {
		if (e.getLevel() == LogService.LOG_ERROR) {
			errorLog = true;
		}
	}

	/**
	 * This method tests the behaviour of the service component runtime against
	 * bad component definitions.
	 *
	 * It will perform the following checks:
	 *
	 * <code>
	 * + install bundle with illegal component definition
	 *   verify the following illegal conditions:
	 *  - more that one implementation definition
	 *  - both factory &amp; serviceFactory are specified
	 *  - reference has a bind method but doesn't have unbind and reverse
	 * + check if framework error is posted
	 * </code>
	 *
	 * @throws Exception can be thrown at any time
	 */
	@Test
	public void testBadComponents() throws Exception {
		BundleContext bc = context;

		tb1.uninstall();

		// clear any previously reported 'error' bundles
		errorLog = false;
		LogReaderService logService = context.getService(serviceReference(LogReaderService.class));
		logService.addLogListener(this);

		// the bundle contains some illegal definitions
		tb1 = installer.installBundle(context.getBundle().getEntry("tb1.jar"), false);
		tb1.start();
		settle(SLEEP * 2); // log and scr have asynchronous processing

		logService.removeLogListener(this);

		// make sure that SCR reports some errors for tb1
		assertThat(errorLog).as("The Service Component Runtime should post a log error for tb1").isTrue();

		// make sure that the services are not registered
		ServiceReference< ? > ref;
		ref = bc.getServiceReference(TEST_CASE_ROOT + ".tb1.BadService1");
		assertThat(ref).as("The BadService1 shouldn't be registered because the XML contains two implementation classes").isNull();

		ref = bc.getServiceReference(TEST_CASE_ROOT + ".tb1.BadService2");
		assertThat(ref).as("The BadService2 shouldn't be registered because component & service factories are incompatible").isNull();
	}

	private <S> ServiceReference<S> serviceReference(Class<S> clazz) {
		ServiceReference<S> ref = context.getServiceReference(clazz);
		assertThat(ref)
				.as("Can't get service reference for " + clazz.getName())
				.isNotNull();
		return ref;
	}

	private Bundle getSCRBundle() {
		ServiceReference<ServiceComponentRuntime> ref = serviceReference(
				ServiceComponentRuntime.class);
		ServiceComponentRuntime scr = context.getService(ref);
		assertThat(scr).as("ServiceComponentRuntime service").isNotNull();
		try {
			assertThat(ref).as("ServiceComponentRuntime service reference")
					.isNotNull();
			Bundle bundle = ref.getBundle();
			assertThat(bundle).as("ServiceComponentRuntime bundle").isNotNull();
			return bundle;
		} finally {
			context.ungetService(ref);
		}
	}

	// helper for testDynamicBind
	private int getCount() {
		TBCService serviceConsumerEvent = (TBCService) trackerConsumerEvent
				.getService();
		Dictionary<String,Object> props = serviceConsumerEvent.getProperties();
		Integer x = (Integer) props.get("count");
		return x.intValue();
	}

	// tb3 service has dynamic bind
	@Test
	public void testDynamicBind() throws Exception {
		assertThat(getCount()).as("The number of dynamically bound service should be zero").isZero();

		Bundle bundle = installer.installBundle(context.getBundle().getEntry("tb4.jar"), false);
		bundle.start();
		settle(SLEEP);

		assertThat(getCount()).as("No instance created yet, so the bind count must be zero").isZero();

		ComponentFactory<Object> factory = trackerNamedServiceFactory
				.getService();

		// create the first service!
		Hashtable<String,Object> props = new Hashtable<>();
		props.put("name", "hello");
		ComponentInstance<Object> instance1 = factory.newInstance(props);
		settle(SLEEP);

		assertThat(getCount()).as("The component should be bound to the first instance").isOne();

		props = new Hashtable<>();
		props.put("name", "world");
		// ComponentInstance instance2 =
		factory.newInstance(props);
		settle(SLEEP);
		assertThat(getCount()).as("The component should be bound to the second instance too").isEqualTo(2);

		instance1.dispose();
		settle(SLEEP);
		assertThat(getCount()).as("The component should be unbound from the first instance").isOne();

		bundle.uninstall(); // must also dispose the second instance!
		settle(SLEEP);
		assertThat(getCount()).as("The component should be unbound from the all component instances").isZero();
	}

	@Test
	public void testCMUpdate() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		ServiceReference<Object> ref;
		Object service;
		String cmprop;

		// verify that the current service is loaded with default values from
		// the XML file
		ref = trackerConsumerLookup.getServiceReference();
		cmprop = (String) ref.getProperty("cmprop");
		service = trackerConsumerLookup.getService();
		assertThat(cmprop).as("The service should be registered from the property defined in the XML file").isEqualTo("setFromXML");

		// update the properties
		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("cmprop", "setFromCM");
		// the line below will create the configuration if it doesn't exists!
		// see CM api for details
		Configuration config = cm.getConfiguration(LOOKUP_CLASS, null);
		config.update(props);

		// let SCR & CM to complete it's job
		settle(SLEEP * 2);

		// verify that the service is updated
		assertThat(trackerConsumerLookup.getService()).as("The service shoud be restarted with a new instance").isNotSameAs(service);
		service = trackerConsumerLookup.getService();
		// verify that the property is overriden with the CM value
		ref = trackerConsumerLookup.getServiceReference();
		cmprop = (String) ref.getProperty("cmprop");
		assertThat(cmprop).as("The property 'cmprop' should be overriden with the value from CM").isEqualTo("setFromCM");
		Object otherProp = ref.getProperty("test.property.string");
		assertThat(otherProp).as("The CM update shouldn't delete the other properties defined in XML file").isNotNull();

		// make sure that the SCR handles delete events!
		config.delete();
		// wait to complete - slow becase CM may use threads for notify
		settle(SLEEP * 2);

		assertThat(trackerConsumerLookup.getService()).as("After delete the service shoud be restarted with a new instance").isNotSameAs(service);
		ref = trackerConsumerLookup.getServiceReference();
		cmprop = (String) ref.getProperty("cmprop");
		assertThat(cmprop).as("After delete the service should be registered from the property defined in the XML file").isEqualTo("setFromXML");
	}

	// Notice: "serviceFactory" attribute means "an OSGi Service Factory"
	// which is quite different from the "Managed Service Factory" which is
	// CM related.
	@Test
	public void testManagedServiceFactory() throws Exception {
		// check the service is instantiated without properties!
		int length = trackerConsumerEvent.getServices().length;
		Object service = trackerConsumerEvent.getService();
		assertThat(length).as("The event service should be registered even if no properties are set!").isOne();

		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		Configuration config1;
		Configuration config2;
		Hashtable<String,Object> props;

		// create a new configuration
		config1 = cm.createFactoryConfiguration(EVENT_CLASS, null);
		props = new Hashtable<>(2);
		props.put("instance", "1");
		config1.update(props);
		settle(SLEEP * 2); // let it finish update, CM + SCR

		// verify the result
		length = trackerConsumerEvent.getServices().length;
		assertThat(length).as("The service count shouldn't be changed!").isOne();
		assertThat(trackerConsumerEvent.getService()).as("The service should be updated with new instance").isNotSameAs(service);

		// create second configuration
		config2 = cm.createFactoryConfiguration(EVENT_CLASS, null);
		props = new Hashtable<>(2);
		props.put("instance", "2");
		config2.update(props);
		settle(SLEEP * 2); // let it finish update, CM + SCR

		// verify the result
		length = trackerConsumerEvent.getServices().length;
		assertThat(length).as("For each configuration should be a service instance").isEqualTo(2);

		config1.delete();
		config2.delete();
	}

	@Test
	public void testComponentEnableAndDisable() throws Exception {
		int count;

		// initially the DynService shouldn't be register because it's disabled
		count = trackerDyn.getServices() == null ? 0
				: trackerDyn.getServices().length;
		assertThat(count).as("The DynService shouldn't be instantiated because it is disabled").isZero();

		// enable the all services (actually only dyn service)
		ComponentEnabler lookupService = (ComponentEnabler) trackerConsumerLookup
				.getService();
		lookupService.enableComponent(null, true);
		settle(SLEEP); // let SCR complete

		// verify that it is instantiate
		count = trackerDyn.getServices() == null ? 0
				: trackerDyn.getServices().length;
		assertThat(count).as("The DynService must be enabled and instantiated").isOne();

		// disable dyn service
		lookupService.enableComponent(DYN_CLASS, false);
		settle(SLEEP); // let SCR complete

		// verify that DYN services is not available
		count = trackerDyn.getServices() == null ? 0
				: trackerDyn.getServices().length;
		assertThat(count).as("The DynService must be disabled & removed again").isZero();
	}

	@Test
	public void testConfigurationPolicy() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb5 = installer.installBundle(context.getBundle().getEntry("tb5.jar"), false);
		tb5.start();
		waitBundleStart();

		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("config.base.data", Integer.valueOf(1));

		// component notsetNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0
		assertThat(getBaseConfigData(COMP_NOTSET_100)).as("Configuration data should not be available for notsetNS100").isZero();
		// component notsetNS100 - property set by Configuration Admin; XML NS
		// 1.0.0
		Configuration config = cm.getConfiguration(COMP_NOTSET_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_NOTSET_100)).as("Configuration data should be available for notsetNS100 and equal to 1").isOne();

		// component notsetNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_NOTSET_110)).as("Configuration data should not be available for notsetNS110").isZero();
		// component notsetNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.getConfiguration(COMP_NOTSET_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_NOTSET_110)).as("Configuration data should be available for notsetNS110 and equal to 1").isOne();

		// component optionalNS100 - property not set by Configuration Admin;
		// XML NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_OPTIONAL_100)).as("Component optionalNS100 should not be activated").isEqualTo(-1);
		// component optionalNS100 - property set by Configuration Admin; XML NS
		// 1.0.0 - INVALID COMPONENT
		config = cm.getConfiguration(COMP_OPTIONAL_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_OPTIONAL_100)).as("Component optionalNS100 should not be activated").isEqualTo(-1);

		// component optionalNS110 - property not set by Configuration Admin;
		// XML NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_OPTIONAL_110)).as("Configuration data should not be available for optionalNS110").isZero();
		// component optionalNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.getConfiguration(COMP_OPTIONAL_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_OPTIONAL_110)).as("Configuration data should be available for optionalNS110 and equal to 1").isOne();

		// component requireNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_REQUIRE_100)).as("Component requireNS100 should not be activated").isEqualTo(-1);
		// component requireNS100 - property set by Configuration Admin; XML NS
		// 1.0.0 - INVALID COMPONENT
		config = cm.getConfiguration(COMP_REQUIRE_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_REQUIRE_100)).as("Component requireNS100 should not be activated").isEqualTo(-1);

		// component requireNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_REQUIRE_110)).as("Configuration data should not be available for requireNS110, and it should not be satisfied").isEqualTo(-1);
		// component requireNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.getConfiguration(COMP_REQUIRE_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_REQUIRE_110)).as("Configuration data should be available for requireNS110 and equal to 1").isOne();

		// component ignoreNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_IGNORE_100)).as("Component ignoreNS100 should not be activated").isEqualTo(-1);
		// component ignoreNS100 - property set by Configuration Admin; XML NS
		// 1.0.0
		// - INVALID COMPONENT
		config = cm.getConfiguration(COMP_IGNORE_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_IGNORE_100)).as("Component ignoreNS100 should not be activated").isEqualTo(-1);

		// component ignoreNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_IGNORE_110)).as("Configuration data should not be available for ignoreNS110, but it should be satisfied").isZero();
		// component ignoreNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.getConfiguration(COMP_IGNORE_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_IGNORE_110)).as("Configuration data should not be available for ignoreNS110, but it should be satisfied").isZero();

		tb5.uninstall();
	}

	// tests configuration-policy for factory configuration objects
	@Test
	public void testConfigurationPolicyFactoryConf() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb5 = installer.installBundle(context.getBundle().getEntry("tb5.jar"), false);
		tb5.start();
		waitBundleStart();

		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("config.base.data", Integer.valueOf(1));

		// component notsetNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0
		assertThat(getBaseConfigData(COMP_NOTSET_100)).as("Configuration data should not be available for notsetNS100").isZero();
		// component notsetNS100 - property set by Configuration Admin; XML NS
		// 1.0.0
		Configuration config = cm.createFactoryConfiguration(COMP_NOTSET_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_NOTSET_100)).as("Configuration data should be available for notsetNS100 and equal to 1").isOne();

		// component notsetNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_NOTSET_110)).as("Configuration data should not be available for notsetNS110").isZero();
		// component notsetNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.createFactoryConfiguration(COMP_NOTSET_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_NOTSET_110)).as("Configuration data should be available for notsetNS110 and equal to 1").isOne();

		// component optionalNS100 - property not set by Configuration Admin;
		// XML NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_OPTIONAL_100)).as("Component optionalNS100 should not be activated").isEqualTo(-1);
		// component optionalNS100 - property set by Configuration Admin; XML NS
		// 1.0.0 - INVALID COMPONENT
		config = cm.createFactoryConfiguration(COMP_OPTIONAL_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_OPTIONAL_100)).as("Component optionalNS100 should not be activated").isEqualTo(-1);

		// component optionalNS110 - property not set by Configuration Admin;
		// XML NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_OPTIONAL_110)).as("Configuration data should not be available for optionalNS110").isZero();
		// component optionalNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.createFactoryConfiguration(COMP_OPTIONAL_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_OPTIONAL_110)).as("Configuration data should be available for optionalNS110 and equal to 1").isOne();

		// component requireNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_REQUIRE_100)).as("Component requireNS100 should not be activated").isEqualTo(-1);
		// component requireNS100 - property set by Configuration Admin; XML NS
		// 1.0.0 - INVALID COMPONENT
		config = cm.createFactoryConfiguration(COMP_REQUIRE_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_REQUIRE_100)).as("Component requireNS100 should not be activated").isEqualTo(-1);

		// component requireNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_REQUIRE_110)).as("Configuration data should not be available for requireNS110, and it should not be satisfied").isEqualTo(-1);
		// component requireNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.createFactoryConfiguration(COMP_REQUIRE_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_REQUIRE_110)).as("Configuration data should be available for requireNS110 and equal to 1").isOne();

		// component ignoreNS100 - property not set by Configuration Admin; XML
		// NS
		// 1.0.0 - INVALID COMPONENT
		assertThat(getBaseConfigData(COMP_IGNORE_100)).as("Component ignoreNS100 should not be activated").isEqualTo(-1);
		// component ignoreNS100 - property set by Configuration Admin; XML NS
		// 1.0.0
		// - INVALID COMPONENT
		config = cm.createFactoryConfiguration(COMP_IGNORE_100, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_IGNORE_100)).as("Component ignoreNS100 should not be activated").isEqualTo(-1);

		// component ignoreNS110 - property not set by Configuration Admin; XML
		// NS
		// 1.1.0
		assertThat(getBaseConfigData(COMP_IGNORE_110)).as("Configuration data should not be available for ignoreNS110, but it should be satisfied").isZero();
		// component ignoreNS110 - property set by Configuration Admin; XML NS
		// 1.1.0
		config = cm.createFactoryConfiguration(COMP_IGNORE_110, null);
		config.update(props);
		settle(SLEEP * 3);
		assertThat(getBaseConfigData(COMP_IGNORE_110)).as("Configuration data should not be available for ignoreNS110, but it should be satisfied").isZero();

		tb5.uninstall();
	}

	@Test
	public void testActivateDeactivate() throws Exception {
		System.out.println("install tb6");
		Bundle tb6 = installer.installBundle(context.getBundle().getEntry("tb6.jar"), false);
		System.out.println("installed tb6");
		System.out.println("start tb6");
		tb6.start();
		System.out.println("started tb6");
		settle(SLEEP * 3);

		final String NOTSET_NS100 = TEST_CASE_ROOT + ".tb6.notsetNS100";
		final String NOTSET_NS110 = TEST_CASE_ROOT + ".tb6.notsetNS110";
		final String NOARGS_NS100 = TEST_CASE_ROOT + ".tb6.NoArgsNS100";
		final String NOARGS_NS110 = TEST_CASE_ROOT + ".tb6.NoArgsNS110";
		final String CC_NS100 = TEST_CASE_ROOT + ".tb6.CcNS100";
		final String CC_NS110 = TEST_CASE_ROOT + ".tb6.CcNS110";
		final String BC_NS100 = TEST_CASE_ROOT + ".tb6.BcNS100";
		final String BC_NS110 = TEST_CASE_ROOT + ".tb6.BcNS110";
		final String MAP_NS100 = TEST_CASE_ROOT + ".tb6.MapNS100";
		final String MAP_NS110 = TEST_CASE_ROOT + ".tb6.MapNS110";
		final String CC_BC_MAP_NS100 = TEST_CASE_ROOT + ".tb6.CcBcMapNS100";
		final String CC_BC_MAP_NS110 = TEST_CASE_ROOT + ".tb6.CcBcMapNS110";
		final String INT_NS110 = TEST_CASE_ROOT + ".tb6.IntNS110";
		final String CC_BC_MAP_INT_NS110 = TEST_CASE_ROOT
				+ ".tb6.CcBcMapIntNS110";
		final String CONT_EXP = TEST_CASE_ROOT + ".tb6.ContExp";

		BaseService bs = getBaseService(NOTSET_NS100);
		ComponentContext cc = (bs instanceof ComponentContextExposer) ? ((ComponentContextExposer) bs)
				.getComponentContext()
				: null;
		assertThat(cc).as("Component context should be available").isNotNull();

		assertThat(checkDataBits(ActDeactComponent.ACTIVATE_CC,
						getBaseConfigData(bs))).as("Activate method of " + NOTSET_NS100 + " should be called").isTrue();
		System.out.println("disable " + NOTSET_NS100);
		cc.disableComponent(NOTSET_NS100);
		System.out.println("disabled " + NOTSET_NS100);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACTIVATE_CC,
						getBaseConfigData(bs))).as("Deactivate method of " + NOTSET_NS100 + " should be called").isTrue();

		bs = getBaseService(NOTSET_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACTIVATE_CC,
						getBaseConfigData(bs))).as("Activate method of " + NOTSET_NS110 + " should be called").isTrue();
		System.out.println("disable " + NOTSET_NS110);
		cc.disableComponent(NOTSET_NS110);
		System.out.println("disabled " + NOTSET_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACTIVATE_CC,
						getBaseConfigData(bs))).as("Deactivate method of " + NOTSET_NS110 + " should be called").isTrue();

		bs = getBaseService(NOARGS_NS100); // INVALID COMPONENT FOR XML NS 1.0.0
		assertThat(getBaseConfigData(bs)).as("Component " + NOARGS_NS100 + " should not be activated").isEqualTo(-1);

		bs = getBaseService(NOARGS_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACT, getBaseConfigData(bs))).as("Activate method of " + NOARGS_NS110 + " should be called").isTrue();
		System.out.println("disable " + NOARGS_NS110);
		cc.disableComponent(NOARGS_NS110);
		System.out.println("disabled " + NOARGS_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT, getBaseConfigData(bs))).as("Deactivate method of " + NOARGS_NS110 + " should be called").isTrue();

		bs = getBaseService(CC_NS100); // INVALID COMPONENT FOR XML NS 1.0.0
		assertThat(getBaseConfigData(bs)).as("Component " + CC_NS100 + " should not be activated").isEqualTo(-1);

		bs = getBaseService(CC_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACT_CC, getBaseConfigData(bs))).as("Activate method of " + CC_NS110 + " should be called").isTrue();
		System.out.println("disable " + CC_NS110);
		cc.disableComponent(CC_NS110);
		System.out.println("disabled " + CC_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT_CC, getBaseConfigData(bs))).as("Deactivate method of " + CC_NS110 + " should be called").isTrue();

		bs = getBaseService(BC_NS100); // INVALID COMPONENT FOR XML NS 1.0.0
		assertThat(getBaseConfigData(bs)).as("Component " + BC_NS100 + " should not be activated").isEqualTo(-1);

		bs = getBaseService(BC_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACT_BC, getBaseConfigData(bs))).as("Activate method of " + BC_NS110 + " should be called").isTrue();
		System.out.println("disable " + BC_NS110);
		cc.disableComponent(BC_NS110);
		System.out.println("disabled " + BC_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT_BC, getBaseConfigData(bs))).as("Deactivate method of " + BC_NS110 + " should be called").isTrue();

		bs = getBaseService(MAP_NS100); // INVALID COMPONENT FOR XML NS 1.0.0
		assertThat(getBaseConfigData(bs)).as("Component " + MAP_NS100 + " should not be activated").isEqualTo(-1);

		bs = getBaseService(MAP_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACT_MAP, getBaseConfigData(bs))).as("Activate method of " + MAP_NS110 + " should be called").isTrue();
		System.out.println("disable " + MAP_NS110);
		cc.disableComponent(MAP_NS110);
		System.out.println("disabled " + MAP_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT_MAP,
						getBaseConfigData(bs))).as("Deactivate method of " + MAP_NS110 + " should be called").isTrue();

		bs = getBaseService(CC_BC_MAP_NS100); // INVALID COMPONENT FOR XML NS
												// 1.0.0
		assertThat(getBaseConfigData(bs)).as("Component " + CC_BC_MAP_NS100
				+ " should not be activated").isEqualTo(-1);

		bs = getBaseService(CC_BC_MAP_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		assertThat(checkDataBits(ActDeactComponent.ACT_CC_BC_MAP,
						getBaseConfigData(bs))).as("Activate method of " + CC_BC_MAP_NS110
				+ " should be called").isTrue();
		System.out.println("disable " + CC_BC_MAP_NS110);
		cc.disableComponent(CC_BC_MAP_NS110);
		System.out.println("disabled " + CC_BC_MAP_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT_CC_BC_MAP,
						getBaseConfigData(bs))).as("Deactivate method of " + CC_BC_MAP_NS110
				+ " should be called").isTrue();

		bs = getBaseService(INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		System.out.println("disable " + INT_NS110);
		cc.disableComponent(INT_NS110);
		System.out.println("disabled " + INT_NS110);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ActDeactComponent.DEACT_INT,
						getBaseConfigData(bs))).as("Deactivate method of " + INT_NS110 + " should be called").isTrue();

		bs = getBaseService(CC_BC_MAP_INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		System.out.println("disable " + CC_BC_MAP_INT_NS110);
		cc.disableComponent(CC_BC_MAP_INT_NS110);
		System.out.println("disabled " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);
		long data = getBaseConfigData(bs);
		assertThat(checkDataBits(ActDeactComponent.DEACT_CC_BC_MAP_INT, data)).as("Deactivate method of " + CC_BC_MAP_INT_NS110
				+ " should be called").isTrue();

		// // Testing Deactivation reasons ////
		assertThat(0xFF & (data >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_DISABLED").isEqualTo(ComponentConstants.DEACTIVATION_REASON_DISABLED);

		System.out.println("enable " + CC_BC_MAP_INT_NS110);
		cc.enableComponent(CC_BC_MAP_INT_NS110);
		System.out.println("enabled " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);
		bs = getBaseService(CC_BC_MAP_INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		System.out.println("disable " + CONT_EXP);
		cc.disableComponent(CONT_EXP);
		System.out.println("disabled " + CONT_EXP);
		settle(SLEEP * 3);
		assertThat(0xFF & (getBaseConfigData(bs) >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_REFERENCE").isEqualTo(ComponentConstants.DEACTIVATION_REASON_REFERENCE);

		System.out.println("enable " + CONT_EXP);
		cc.enableComponent(CONT_EXP);
		System.out.println("enabled " + CONT_EXP);
		settle(SLEEP * 3);

		bs = getBaseService(CC_BC_MAP_INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		Configuration config = cm.getConfiguration(CC_BC_MAP_INT_NS110, null);
		Dictionary<String,Object> properties = config.getProperties();
		if (properties == null) {
			properties = new Hashtable<>();
		}
		properties.put("configuration.dummy", "dummy");
		System.out.println("configuration update " + CC_BC_MAP_INT_NS110);
		config.update(properties);
		System.out.println("configuration updated " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);
		assertThat(0xFF & (getBaseConfigData(bs) >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_CONFIGURATION_MODIFIED").isEqualTo(ComponentConstants.DEACTIVATION_REASON_CONFIGURATION_MODIFIED);

		System.out.println("enable " + CC_BC_MAP_INT_NS110);
		cc.enableComponent(CC_BC_MAP_INT_NS110);
		System.out.println("enabled " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);

		bs = getBaseService(CC_BC_MAP_INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		config = cm.getConfiguration(CC_BC_MAP_INT_NS110, null);
		System.out.println("configuration delete " + CC_BC_MAP_INT_NS110);
		config.delete();
		System.out.println("configuration deleted " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);
		assertThat(0xFF & (getBaseConfigData(bs) >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_CONFIGURATION_DELETED").isEqualTo(ComponentConstants.DEACTIVATION_REASON_CONFIGURATION_DELETED);

		System.out.println("enable " + CC_BC_MAP_INT_NS110);
		cc.enableComponent(CC_BC_MAP_INT_NS110);
		System.out.println("enabled " + CC_BC_MAP_INT_NS110);
		settle(SLEEP * 3);

		System.out.println("enable " + INT_NS110);
		cc.enableComponent(INT_NS110);
		System.out.println("enabled " + INT_NS110);
		settle(SLEEP * 3);

		bs = getBaseService(INT_NS110);
		assertThat(bs).as("bs should not be null").isNotNull();
		ComponentContext ccIntNS110 = (bs instanceof ComponentContextExposer) ? ((ComponentContextExposer) bs)
				.getComponentContext()
				: null;
		assertThat(ccIntNS110).as("Component context should be available for " + INT_NS110).isNotNull();
		System.out.println("dispose " + INT_NS110);
		ccIntNS110.getComponentInstance().dispose();
		System.out.println("disposed " + INT_NS110);
		settle(SLEEP * 3);
		assertThat(0xFF & (getBaseConfigData(bs) >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_DISPOSED").isEqualTo(ComponentConstants.DEACTIVATION_REASON_DISPOSED);

		bs = getBaseService(CONT_EXP);
		assertThat(bs).as("bs should not be null").isNotNull();
		System.out.println("stop tb6");
		tb6.stop();
		System.out.println("stopped tb6");
		settle(SLEEP * 3);
		assertThat(0xFF & (getBaseConfigData(bs) >> 16)).as("Deactivation reason shall be DEACTIVATION_REASON_BUNDLE_STOPPED").isEqualTo(ComponentConstants.DEACTIVATION_REASON_BUNDLE_STOPPED);

		System.out.println("uninstall tb6");
		tb6.uninstall();
		System.out.println("uninstalled tb6");
	}

	@Test
	public void testEventMethods110() throws Exception {
		final String SR_NS100 = TEST_CASE_ROOT + ".tb7.SrNS100";
		final String SR_NS110 = TEST_CASE_ROOT + ".tb7.SrNS110";
		final String CE_NS100 = TEST_CASE_ROOT + ".tb7.CeNS100";
		final String CE_NS110 = TEST_CASE_ROOT + ".tb7.CeNS110";
		final String CE_MAP_NS100 = TEST_CASE_ROOT + ".tb7.CeMapNS100";
		final String CE_MAP_NS110 = TEST_CASE_ROOT + ".tb7.CeMapNS110";

		Bundle tb7 = installer.installBundle(context.getBundle().getEntry("tb7.jar"), false);
		assertThat(tb7).as("tb7 failed to install").isNotNull();

		try {
			tb7.start();
			settle(SLEEP * 3);

			ServiceReference<ComponentEnabler> ref = context
					.getServiceReference(ComponentEnabler.class);
			assertThat(ref).as("Component Enabler Service Reference should be available").isNotNull();
			ComponentEnabler enabler = context
					.getService(ref);
			assertThat(enabler).as("Component Enabler Service should be available").isNotNull();

			try {
				BaseService bs = getBaseService(SR_NS100);
				assertThat(bs).as("Component " + SR_NS100 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_1,
								getBaseConfigData(bs))).as("Bind method of " + SR_NS100 + " should be called").isTrue();
				enabler.enableComponent(SR_NS100, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_1,
								getBaseConfigData(bs))).as("Unbind method of " + SR_NS100 + " should be called").isTrue();

				bs = getBaseService(SR_NS110);
				assertThat(bs).as("Component " + SR_NS110 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_1,
								getBaseConfigData(bs))).as("Bind method of " + SR_NS110 + " should be called").isTrue();
				enabler.enableComponent(SR_NS110, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_1,
								getBaseConfigData(bs))).as("Unbind method of " + SR_NS110 + " should be called").isTrue();

				bs = getBaseService(CE_NS100);
				assertThat(bs).as("Component " + CE_NS100 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_2,
								getBaseConfigData(bs))).as("Bind method of " + CE_NS100 + " should be called").isTrue();
				enabler.enableComponent(CE_NS100, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_2,
								getBaseConfigData(bs))).as("Unbind method of " + CE_NS100 + " should be called").isTrue();

				bs = getBaseService(CE_NS110);
				assertThat(bs).as("Component " + CE_NS110 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_2,
								getBaseConfigData(bs))).as("Bind method of " + CE_NS110 + " should be called").isTrue();
				enabler.enableComponent(CE_NS110, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_2,
								getBaseConfigData(bs))).as("Unbind method of " + CE_NS110 + " should be called").isTrue();

				bs = getBaseService(CE_MAP_NS100);
				assertThat(bs).as("Component " + CE_MAP_NS100
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_3,
								getBaseConfigData(bs))).as("Bind method of " + CE_MAP_NS100
								+ " should not be called").isFalse();

				enabler.enableComponent(CE_MAP_NS100, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_3,
								getBaseConfigData(bs))).as("Unbind method of " + CE_MAP_NS100
								+ " should not be called").isFalse();

				bs = getBaseService(CE_MAP_NS110);
				assertThat(bs).as("Component " + CE_MAP_NS110
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_3,
								getBaseConfigData(bs))).as("Bind method of " + CE_MAP_NS110 + " should be called").isTrue();
				enabler.enableComponent(CE_MAP_NS110, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_3,
								getBaseConfigData(bs))).as("Unbind method of " + CE_MAP_NS110
								+ " should be called").isTrue();
			}
			finally {
				context.ungetService(ref);
			}
		}
		finally {
			tb7.uninstall();
		}
	}

	@Test
	public void testEventMethods130() throws Exception {

		Bundle tb18 = installer.installBundle(context.getBundle().getEntry("tb18.jar"), false);
		assertThat(tb18).as("tb18 failed to install").isNotNull();
		
		try {
			tb18.start();
			settle(SLEEP * 3);

			ServiceReference<ComponentEnabler> ref = context
					.getServiceReference(ComponentEnabler.class);
			assertThat(ref).as("Component Enabler Service Reference should be available").isNotNull();
			ComponentEnabler enabler = context
					.getService(ref);
			assertThat(enabler).as("Component Enabler Service should be available").isNotNull();

			try {
				final String COMPONENT_1 = TEST_CASE_ROOT + ".tb18.1";
				BaseService bs = getBaseService(COMPONENT_1);
				assertThat(bs).as("Component " + COMPONENT_1 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.ERROR_1,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_1 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.BIND_1,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_1 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_1, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.ERROR_1,
								getBaseConfigData(bs))).as("Wrong unbind method of " + COMPONENT_1 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.UNBIND_1,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_1 + " should be called").isTrue();

				final String COMPONENT_2 = TEST_CASE_ROOT + ".tb18.2";
				bs = getBaseService(COMPONENT_2);
				assertThat(bs).as("Component " + COMPONENT_2 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.ERROR_2,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_2 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.BIND_2,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_2 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_2, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.ERROR_2,
								getBaseConfigData(bs))).as("Wrong unbind method of " + COMPONENT_2 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.UNBIND_2,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_2 + " should be called").isTrue();

				final String COMPONENT_3 = TEST_CASE_ROOT + ".tb18.3";
				bs = getBaseService(COMPONENT_3);
				assertThat(bs).as("Component " + COMPONENT_3 + " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_3,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_3 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_3, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_3,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_3 + " should be called").isTrue();

				final String COMPONENT_4 = TEST_CASE_ROOT + ".tb18.4";
				bs = getBaseService(COMPONENT_4);
				assertThat(bs).as("Component " + COMPONENT_4
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_4,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_4 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_4, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_4,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_4 + " should be called").isTrue();

				final String COMPONENT_5 = TEST_CASE_ROOT + ".tb18.5";
				bs = getBaseService(COMPONENT_5);
				assertThat(bs).as("Component " + COMPONENT_5
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.ERROR_5,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_5 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.BIND_5,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_5 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_5, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.ERROR_5,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_5 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.UNBIND_5,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_5 + " should be called").isTrue();

				final String COMPONENT_6 = TEST_CASE_ROOT + ".tb18.6";
				bs = getBaseService(COMPONENT_6);
				assertThat(bs).as("Component " + COMPONENT_6
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.BIND_6,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_6 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_6, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.UNBIND_6,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_6 + " should be called").isTrue();

				final String COMPONENT_7 = TEST_CASE_ROOT + ".tb18.7";
				bs = getBaseService(COMPONENT_7);
				assertThat(bs).as("Component " + COMPONENT_7
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.ERROR_7,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_7 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.BIND_7,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_7 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_7, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.ERROR_7,
								getBaseConfigData(bs))).as("Wrong unbind method of " + COMPONENT_7 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.UNBIND_7,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_7 + " should be called").isTrue();

				final String COMPONENT_8 = TEST_CASE_ROOT + ".tb18.8";
				bs = getBaseService(COMPONENT_8);
				assertThat(bs).as("Component " + COMPONENT_8
						+ " should be activated").isNotNull();
				assertThat(checkDataBits(DSTestConstants.ERROR_8,
								getBaseConfigData(bs))).as("Wrong bind method of " + COMPONENT_8 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.BIND_8,
								getBaseConfigData(bs))).as("Bind method of " + COMPONENT_8 + " should be called").isTrue();
				enabler.enableComponent(COMPONENT_8, false);
				settle(SLEEP * 3);
				assertThat(checkDataBits(DSTestConstants.ERROR_8,
								getBaseConfigData(bs))).as("Wrong unbind method of " + COMPONENT_8 + " called").isFalse();
				assertThat(checkDataBits(DSTestConstants.UNBIND_8,
								getBaseConfigData(bs))).as("Unbind method of " + COMPONENT_8 + " should be called").isTrue();
			}
			finally {
				context.ungetService(ref);
			}
		}
		finally {
			tb18.uninstall();
		}
	}
	
	@Test
	public void testOptionalNames() throws Exception {
		Bundle tb8 = installer.installBundle(context.getBundle().getEntry("tb8.jar"), false);
		tb8.start();
		settle(SLEEP * 3);
		BaseService bs;

		final String OPT_NAME_100 = TEST_CASE_ROOT + ".tb8.OptionalNames";
		final String OPT_NAME_110 = TEST_CASE_ROOT + ".tb8.OptionalNames2";
		final String OPT_REF_100 = TEST_CASE_ROOT + ".tb8.OptRef100";
		final String OPT_REF_110 = TEST_CASE_ROOT + ".tb8.OptRef110";

		assertThat(getBaseService(OPT_NAME_100)).as("Component " + OPT_NAME_100 + " should not be activated").isNull();
		assertThat(getBaseService(OPT_NAME_110)).as("Component " + OPT_NAME_110 + " should be activated").isNotNull();

		assertThat(getBaseService(OPT_REF_100)).as("Component " + OPT_REF_100 + " should not be activated").isNull();
		assertThat(bs = getBaseService(OPT_REF_110)).as("Component " + OPT_REF_110 + " should be activated").isNotNull();

		ComponentContext cc = (bs instanceof ComponentContextExposer) ? ((ComponentContextExposer) bs)
				.getComponentContext()
				: null;
		assertThat(cc).as("Component context should be available").isNotNull();
		assertThat((Object) cc.locateService(ComponentContextExposer.class.getName())).as("Optional reference name should be set to interface attribute").isNotNull();

		tb8.uninstall();
	}


	  // tests wildcard handling in mf (e.g. Service-Component: OSGI-INF/*.xml)
	  @Test
	  public void testWildcardHandling() throws Exception {
		Bundle tb9 = installer.installBundle(context.getBundle().getEntry("tb9.jar"), false);
	    tb9.start();
	    waitBundleStart();

		final String WILD = TEST_CASE_ROOT + ".tb9.Wildcard";

	    // check that the both components are available
	    assertThat(checkAvailability(WILD + "1")).as("The first Wildcard component should be available").isTrue();
	    assertThat(checkAvailability(WILD + "2")).as("The second Wildcard component should be available").isTrue();

	    tb9.uninstall();
	  }

	@Test
	public void testDisposingMultipleDependencies() throws Exception {
		Bundle tb10 = installer.installBundle(context.getBundle().getEntry("tb10.jar"), false);
		tb10.start();
		waitBundleStart();

		final String C1 = TEST_CASE_ROOT + ".tb10.Component1";
		final String C2 = TEST_CASE_ROOT + ".tb10.Component2";
		final String C3 = TEST_CASE_ROOT + ".tb10.Component3";

		BaseService serviceC1 = getBaseService(C1);
		assertThat(serviceC1).as("Component " + C1 + " should be activated").isNotNull();
		BaseService serviceC2 = getBaseService(C2);
		assertThat(serviceC2).as("Component " + C2 + " should be activated").isNotNull();
		BaseService serviceC3 = getBaseService(C3);
		assertThat(serviceC3).as("Component " + C3 + " should be activated").isNotNull();

		ComponentContext cc = (serviceC1 instanceof ComponentContextExposer) ? ((ComponentContextExposer) serviceC1)
				.getComponentContext()
				: null;
		assertThat(cc).as("Component context should be available").isNotNull();

		cc.disableComponent(C1);
		settle(SLEEP * 3);

		assertThat(getBaseConfigData(serviceC3)).as("Component " + C3 + " should be deactivated first").isZero();
		assertThat(getBaseConfigData(serviceC2)).as("Component " + C2 + " should be deactivated second").isOne();
		assertThat(getBaseConfigData(serviceC1)).as("Component " + C1 + " should be deactivated third").isEqualTo(2);

		tb10.uninstall();
	}

	@Test
	public void testReferenceTargetProperty() throws Exception {
		Bundle tb11 = installer.installBundle(context.getBundle().getEntry("tb11.jar"), false);
		tb11.start();
		waitBundleStart();

		final String EXPOSER = TEST_CASE_ROOT + ".tb11.Exposer";
		final String C1 = TEST_CASE_ROOT + ".tb11.C1";
		final String C2 = TEST_CASE_ROOT + ".tb11.C2";

		BaseService bs = getBaseService(EXPOSER);
		ComponentContext cc = (bs instanceof ComponentContextExposer) ? ((ComponentContextExposer) bs)
				.getComponentContext()
				: null;
		assertThat(cc).as("Component context should be available").isNotNull();

		BaseService serviceC2 = getBaseService(C2);
		// target property of referenced service of component Component2 should
		// not
		// be satisfied
		assertThat(serviceC2).as("Component " + C2
				+ " should not be activated because of unsatisfied reference").isNull();

		cc.enableComponent(C1);
		settle(SLEEP * 3);
		assertThat(getBaseService(C1)).as("Component " + C1 + " should be available").isNotNull();

		serviceC2 = getBaseService(C2);
		// target property of referenced service of component Component2 should
		// now
		// be satisfied
		assertThat(serviceC2).as("Component " + C2 + " should be activated").isNotNull();

		tb11.uninstall();
	}

	@Test
	public void testLazyBundles() throws Exception {
		Bundle tb12 = installer.installBundle(context.getBundle().getEntry("tb12.jar"), false);
		// lazy bundle
		tb12.start(Bundle.START_ACTIVATION_POLICY);
		waitBundleStart();

		final String COMP = TEST_CASE_ROOT + ".tb12.component";

		settle(SLEEP);
		assertThat(trackerBaseService.size() > 0).as("Provided service of Component " + COMP
				+ " should be available.").isTrue();

		tb12.uninstall();
	}

	// Testing modified attribute for XML NS 1.0.0
	@Test
	public void testModified100() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb13 = installer.installBundle(context.getBundle().getEntry("tb13.jar"), false);

		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("config.dummy.data", Integer.valueOf(1));
		cm.getConfiguration(MOD_NOTSET_NS100, null).update(props);
		cm.getConfiguration(MOD_NOARGS_NS100, null).update(props);
		cm.getConfiguration(MOD_CC_NS100, null).update(props);
		cm.getConfiguration(MOD_BC_NS100, null).update(props);
		cm.getConfiguration(MOD_MAP_NS100, null).update(props);
		cm.getConfiguration(MOD_CC_BC_MAP_NS100, null).update(props);

		settle(SLEEP * 3);

		tb13.start();
		waitBundleStart();

		props.put("config.dummy.data", Integer.valueOf(2));
		Hashtable<String,Object> unsatisfyingProps = new Hashtable<>(10);
		unsatisfyingProps.put("ref.target", "(component.name=" + TEST_CASE_ROOT
				+ ".tb13.unexisting.provider)");

		BaseService bs = getBaseService(MOD_NOTSET_NS100);
		assertThat(bs).isNotNull();
		cm.getConfiguration(MOD_NOTSET_NS100, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator.MODIFIED, getBaseConfigData(bs))).as("Modified method of " + MOD_NOTSET_NS100
				+ " should not be called").isFalse();
		assertThat(checkDataBits(ModifyRegistrator.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOTSET_NS100
				+ " should be called").isTrue();
		bs = getBaseService(MOD_NOTSET_NS100);
		cm.getConfiguration(MOD_NOTSET_NS100, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator.MODIFIED, getBaseConfigData(bs))).as("Modified method of " + MOD_NOTSET_NS100
				+ " should not be called").isFalse();
		assertThat(checkDataBits(ModifyRegistrator.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOTSET_NS100
				+ " should be called").isTrue();

		// INVALID COMPONENTS for XML NS 1.0.0 - modified attribute is set
		bs = getBaseService(MOD_NOARGS_NS100);
		assertThat(bs).as("Component " + MOD_NOARGS_NS100
				+ " should not be activated").isEqualTo(null);
		bs = getBaseService(MOD_CC_NS100);
		assertThat(bs).as("Component " + MOD_CC_NS100 + " should not be activated").isEqualTo(null);
		bs = getBaseService(MOD_BC_NS100);
		assertThat(bs).as("Component " + MOD_BC_NS100 + " should not be activated").isEqualTo(null);
		bs = getBaseService(MOD_MAP_NS100);
		assertThat(bs).as("Component " + MOD_MAP_NS100 + " should not be activated").isEqualTo(null);
		bs = getBaseService(MOD_CC_BC_MAP_NS100);
		assertThat(bs).as("Component " + MOD_CC_BC_MAP_NS100
				+ " should not be activated").isEqualTo(null);

		tb13.uninstall();
	}

	// Testing modified attribute for XML NS 1.1.0
	@Test
	public void testModified110() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb13a = installer.installBundle(context.getBundle().getEntry("tb13a.jar"), false);

		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("config.dummy.data", Integer.valueOf(1));
		cm.getConfiguration(MOD_NOTSET_NS110, null).update(props);
		cm.getConfiguration(MOD_NOARGS_NS110, null).update(props);
		cm.getConfiguration(MOD_CC_NS110, null).update(props);
		cm.getConfiguration(MOD_BC_NS110, null).update(props);
		cm.getConfiguration(MOD_MAP_NS110, null).update(props);
		cm.getConfiguration(MOD_CC_BC_MAP_NS110, null).update(props);

		settle(SLEEP * 3);

		tb13a.start();
		waitBundleStart();

		props.put("config.dummy.data", Integer.valueOf(2));
		Hashtable<String,Object> unsatisfyingProps = new Hashtable<>(10);
		unsatisfyingProps.put("ref.target", "(component.name=" + TEST_CASE_ROOT
				+ ".tb13.unexisting.provider)");

		BaseService bs = getBaseService(MOD_NOTSET_NS110);
		cm.getConfiguration(MOD_NOTSET_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MODIFIED,
						getBaseConfigData(bs))).as("Modified method of " + MOD_NOTSET_NS110
				+ " should not be called").isFalse();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOTSET_NS110
				+ " should be called").isTrue();
		bs = getBaseService(MOD_NOTSET_NS110);
		cm.getConfiguration(MOD_NOTSET_NS110, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MODIFIED,
						getBaseConfigData(bs))).as("Modified method of " + MOD_NOTSET_NS110
				+ " should not be called").isFalse();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOTSET_NS110
				+ " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_NOTSET_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_NOTSET_NS110
				+ " should be called").isTrue();

		bs = getBaseService(MOD_NOARGS_NS110);
		cm.getConfiguration(MOD_NOARGS_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD, getBaseConfigData(bs))).as("Modified method of " + MOD_NOARGS_NS110
				+ " should be called").isTrue();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOARGS_NS110
				+ " should not be called").isFalse();
		cm.getConfiguration(MOD_NOARGS_NS110, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOARGS_NS110
				+ " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_NOARGS_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_NOARGS_NS110
				+ " should be called").isTrue();

		bs = getBaseService(MOD_CC_NS110);
		cm.getConfiguration(MOD_CC_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD_CC, getBaseConfigData(bs))).as("Modified method of " + MOD_CC_NS110 + " should be called").isTrue();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_CC_NS110
				+ " should not be called").isFalse();
		cm.getConfiguration(MOD_CC_NS110, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_CC_NS110 + " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_CC_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_CC_NS110 + " should be called").isTrue();

		bs = getBaseService(MOD_BC_NS110);
		cm.getConfiguration(MOD_BC_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD_BC, getBaseConfigData(bs))).as("Modified method of " + MOD_BC_NS110 + " should be called").isTrue();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_BC_NS110
				+ " should not be called").isFalse();
		cm.getConfiguration(MOD_BC_NS110, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_BC_NS110 + " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_BC_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_BC_NS110 + " should be called").isTrue();

		bs = getBaseService(MOD_MAP_NS110);
		cm.getConfiguration(MOD_MAP_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD_MAP, getBaseConfigData(bs))).as("Modified method of " + MOD_MAP_NS110 + " should be called").isTrue();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_MAP_NS110
				+ " should not be called").isFalse();
		cm.getConfiguration(MOD_MAP_NS110, null).update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_MAP_NS110
				+ " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_MAP_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_MAP_NS110 + " should be called").isTrue();

		bs = getBaseService(MOD_CC_BC_MAP_NS110);
		cm.getConfiguration(MOD_CC_BC_MAP_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD_CC_BC_MAP,
						getBaseConfigData(bs))).as("Modified method of " + MOD_CC_BC_MAP_NS110
				+ " should be called").isTrue();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_CC_BC_MAP_NS110
				+ " should not be called").isFalse();
		cm.getConfiguration(MOD_CC_BC_MAP_NS110, null)
				.update(unsatisfyingProps);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_CC_BC_MAP_NS110
				+ " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_CC_BC_MAP_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_CC_BC_MAP_NS110
				+ " should be called").isTrue();

		tb13a.uninstall();
	}

	// Testing modified attribute - special cases
	@Test
	public void testModifiedSpecialCases() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb13a = installer.installBundle(context.getBundle().getEntry("tb13a.jar"), false);

		Hashtable<String,Object> props = new Hashtable<>(10);
		props.put("config.dummy.data", Integer.valueOf(1));
		cm.getConfiguration(MOD_CC_NS110, null).update(props);
		cm.getConfiguration(MOD_NOT_EXIST_NS110, null).update(props);
		cm.getConfiguration(MOD_THROW_EX_NS110, null).update(props);
		cm.getConfiguration(MOD_BC_NS110, null).update(props);
		settle(SLEEP * 3);

		System.out.println("\ntb13a starting");
		tb13a.start();
		waitBundleStart();

		// Verifying correctness of updated component properties
		BaseService bs = getBaseService(MOD_CC_NS110);
		props.put("config.dummy.data", Integer.valueOf(2));
		System.out.println("\n" + MOD_CC_NS110 + " config update");
		cm.getConfiguration(MOD_CC_NS110, null).update(props);
		settle(SLEEP * 3);
		Object val = ((ComponentContextExposer) bs).getComponentContext()
				.getProperties().get("config.dummy.data");
		assertThat(checkDataBits(ModifyRegistrator2.MOD_CC, getBaseConfigData(bs))).as("Modified method of " + MOD_CC_NS110 + " should be called").isTrue();
		assertThat((Integer.valueOf(2))).as("Component properties should be updated properly for "
				+ MOD_CC_NS110).isEqualTo(val);

		// Specified modified method doesn't exist, deactivate() should be
		// called
		// instead of modified
		bs = getBaseService(MOD_NOT_EXIST_NS110);
		System.out.println("\n" + MOD_NOT_EXIST_NS110 + " config update");
		cm.getConfiguration(MOD_NOT_EXIST_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_NOT_EXIST_NS110
				+ " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_NOT_EXIST_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_NOT_EXIST_NS110
				+ " should be called").isTrue();

		// Specified modified method throws exception. Normal workflow should
		// continue, deactivate() should not be called
		bs = getBaseService(MOD_THROW_EX_NS110);
		System.out.println("\n" + MOD_THROW_EX_NS110 + " config update");
		cm.getConfiguration(MOD_THROW_EX_NS110, null).update(props);
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_THROW_EX_NS110
				+ " should not be called").isFalse();

		// Deleting component configuration
		bs = getBaseService(MOD_BC_NS110);
		System.out.println("\n" + MOD_BC_NS110 + " config delete");
		cm.getConfiguration(MOD_BC_NS110, null).delete();
		settle(SLEEP * 3);
		assertThat(checkDataBits(ModifyRegistrator2.MOD_BC, getBaseConfigData(bs))).as("Modified method of " + MOD_BC_NS110
				+ " should not be called").isFalse();
		assertThat(checkDataBits(ModifyRegistrator2.DEACTIVATE,
						getBaseConfigData(bs))).as("Deactivate method of " + MOD_BC_NS110 + " should be called").isTrue();
		// Re-activating
		bs = getBaseService(MOD_BC_NS110);
		assertThat(checkDataBits(ModifyRegistrator2.ACTIVATE,
						getBaseConfigData(bs))).as("Activate method of " + MOD_BC_NS110 + " should be called").isTrue();

		System.out.println("\ntb13a stopping");
		tb13a.uninstall();
	}

	@Test
	public void testPrivateProperties() throws Exception {
		Bundle tb14 = installer.installBundle(context.getBundle().getEntry("tb14.jar"), false);
		tb14.start();
		waitBundleStart();

		final String COMP = TEST_CASE_ROOT + ".tb14.component";

		ServiceReference<BaseService> ref = trackerBaseService
				.getServiceReference();
		assertThat(ref).as("Provided service of " + COMP + " should be available").isNotNull();
		String[] keys = ref.getPropertyKeys();
		for (int i = 0; i < keys.length; i++) {
			assertThat(!keys[i]
					.startsWith(".")).as("Private properties should not be propagated").isTrue();
		}

		tb14.uninstall();
	}

	@Test
	public void testUpdatedReference() throws Exception {
		Bundle tb15 = installer.installBundle(context.getBundle().getEntry("tb15.jar"), false);
		try {
			tb15.start();
			waitBundleStart();

			final String KEY = TEST_CASE_ROOT + ".tb15.serviceproperty";
			final String TB15_SVCMAP = TEST_CASE_ROOT + ".tb15.updatedSvcMap";
			final String TB15_SVC = TEST_CASE_ROOT + ".tb15.updatedSvc";
			final String TB15_SR = TEST_CASE_ROOT + ".tb15.updatedSr";
			final String TB15_OVERLOADED1 = TEST_CASE_ROOT
					+ ".tb15.updatedOverloaded1";
			final String TB15_OVERLOADED2 = TEST_CASE_ROOT
					+ ".tb15.updatedOverloaded2";
			final String TB15_BADSIG = TEST_CASE_ROOT + ".tb15.updatedBadSig";
			final String TB15_NOTSET = TEST_CASE_ROOT + ".tb15.updatedNotSet";
			final String TB15_110 = TEST_CASE_ROOT + ".tb15.updated110";
			final String TB15_100 = TEST_CASE_ROOT + ".tb15.updated100";

			BaseService bsSvcMap = getBaseService(TB15_SVCMAP);
			BaseService bsSvc = getBaseService(TB15_SVC);
			BaseService bsSr = getBaseService(TB15_SR);
			BaseService bsOverloaded1 = getBaseService(TB15_OVERLOADED1);
			BaseService bsOverloaded2 = getBaseService(TB15_OVERLOADED2);
			BaseService bsBadSig = getBaseService(TB15_BADSIG);
			BaseService bsNotSet = getBaseService(TB15_NOTSET);
			BaseService bs110 = getBaseService(TB15_110);
			BaseService bs100 = getBaseService(TB15_100);
			assertThat(bsSvcMap).as("Provided service of " + TB15_SVCMAP
					+ " should be available").isNotNull();
			assertThat(bsSvc).as("Provided service of " + TB15_SVC
					+ " should be available").isNotNull();
			assertThat(bsSr).as("Provided service of " + TB15_SR
					+ " should be available").isNotNull();
			assertThat(bsOverloaded1).as("Provided service of " + TB15_OVERLOADED1
					+ " should be available").isNotNull();
			assertThat(bsOverloaded2).as("Provided service of " + TB15_OVERLOADED2
					+ " should be available").isNotNull();
			assertThat(bsBadSig).as("Provided service of " + TB15_BADSIG
					+ " should be available").isNotNull();
			assertThat(bsNotSet).as("Provided service of " + TB15_NOTSET
					+ " should be available").isNotNull();
			assertThat(bs110).as("Provided service of " + TB15_110
					+ " should be available").isNull();
			assertThat(bs100).as("Provided service of " + TB15_100
					+ " should be available").isNull();

			assertThat(bsSvcMap.getProperties()
					.get(KEY)).as("service property not null").isNull();
			assertThat(bsSvc.getProperties().get(KEY)).as("service property not null").isNull();
			assertThat(bsSr.getProperties().get(KEY)).as("service property not null").isNull();
			assertThat(bsOverloaded1
					.getProperties().get(KEY)).as("service property not null").isNull();
			assertThat(bsOverloaded2
					.getProperties().get(KEY)).as("service property not null").isNull();
			assertThat(bsBadSig.getProperties()
					.get(KEY)).as("service property not null").isNull();
			assertThat(bsNotSet.getProperties()
					.get(KEY)).as("service property not null").isNull();

			TestObject service = new TestObject();
			Dictionary<String,Object> props = new Hashtable<>();
			props.put(KEY, "1");
			ServiceRegistration<TestObject> reg = context
					.registerService(TestObject.class, service, props);
			try {
				settle(SLEEP * 3);
				assertThat(bsSvcMap
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsSvc
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsSr
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsOverloaded1.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsOverloaded2.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsNotSet
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsBadSig
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");

				props.put(KEY, "2");
				reg.setProperties(props);
				settle(SLEEP * 3);
				assertThat(bsSvcMap.getProperties().get(KEY)).as("service property incorrect").isEqualTo("updatedSvcMap2");
				assertThat(bsSvc
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("updatedSvc");
				assertThat(bsSr
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("updatedSr2");
				assertThat(bsOverloaded1.getProperties()
								.get(KEY)).as("service property incorrect").isEqualTo("updatedOverloaded1Sr2");
				assertThat(bsOverloaded2.getProperties()
								.get(KEY)).as("service property incorrect").isEqualTo("updatedOverloaded2Svc");
				assertThat(bsBadSig
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");
				assertThat(bsNotSet
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bind1");

				reg.unregister();
				reg = null;
				settle(SLEEP * 3);
				assertThat(bsSvcMap
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsSvc
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsSr
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsOverloaded1.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsOverloaded2.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsBadSig
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
				assertThat(bsNotSet
						.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbind2");
			}
			finally {
				if (reg != null)
					reg.unregister();
			}
		}
		finally {
			tb15.uninstall();
		}
	}

	@Test
	public void testConfigurationPID() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID = TEST_CASE_ROOT + ".tb16.pid1";
		final String KEY = TEST_CASE_ROOT + ".tb16.configproperty";
		final String TB16_REQUIRED = TEST_CASE_ROOT
				+ ".tb16.configurationRequired";
		final String TB16_NOTPRESENT = TEST_CASE_ROOT
				+ ".tb16.configurationNotPresent";
		final String TB16_IGNORED = TEST_CASE_ROOT
				+ ".tb16.configurationIgnored";
		final String TB16_110 = TEST_CASE_ROOT + ".tb16.configuration110";
		final String TB16_100 = TEST_CASE_ROOT + ".tb16.configuration100";

		Configuration config = cm.getConfiguration(PID, null);
		Dictionary<String,Object> props = new Hashtable<>();
		props.put(KEY, "config1");
		config.update(props);
		config = cm.getConfiguration(TB16_REQUIRED, null);
		props = new Hashtable<>();
		props.put(KEY, "bad1");
		config.update(props);
		config = cm.getConfiguration(TB16_NOTPRESENT, null);
		props = new Hashtable<>();
		props.put(KEY, "bad2");
		config.update(props);

		Bundle tb16 = installer.installBundle(context.getBundle().getEntry("tb16.jar"), false);
		try {
			tb16.start();
			waitBundleStart();


			BaseService bsRequired = getBaseService(TB16_REQUIRED);
			BaseService bsNotPesent = getBaseService(TB16_NOTPRESENT);
			BaseService bsIgnored = getBaseService(TB16_IGNORED);
			BaseService bs110 = getBaseService(TB16_110);
			BaseService bs100 = getBaseService(TB16_100);
			assertThat(bsRequired).as("Provided service of " + TB16_REQUIRED
					+ " should be available").isNotNull();
			assertThat(bsNotPesent).as("Provided service of " + TB16_NOTPRESENT
					+ " should be available").isNotNull();
			assertThat(bsIgnored).as("Provided service of " + TB16_IGNORED
					+ " should be available").isNotNull();
			assertThat(bs110).as("Provided service of " + TB16_110
					+ " should be available").isNull();
			assertThat(bs100).as("Provided service of " + TB16_100
					+ " should be available").isNull();

			assertThat(bsRequired
					.getProperties().get(KEY)).as("component property wrong").isEqualTo("config1");
			assertThat(bsNotPesent
					.getProperties().get(KEY)).as("component property wrong").isEqualTo("xml2");
			assertThat(bsIgnored
					.getProperties().get(KEY)).as("component property wrong").isEqualTo("xml3");
			assertThat(bsRequired.getProperties().get(Constants.SERVICE_PID)).as("service.pid property wrong").isEqualTo(PID);

		}
		finally {
			tb16.uninstall();
		}
	}

	@Test
	public void testReferencePolicyOptionStaticReluctant() throws Exception {
		final String KEY = TEST_CASE_ROOT + ".tb17.serviceproperty";
		final String IDENTITY = TEST_CASE_ROOT + ".tb17.identity";
		final String TB17_SR01 = TEST_CASE_ROOT + ".tb17.SR01";
		final String TB17_SR11 = TEST_CASE_ROOT + ".tb17.SR11";
		final String TB17_SR0N = TEST_CASE_ROOT + ".tb17.SR0N";
		final String TB17_SR1N = TEST_CASE_ROOT + ".tb17.SR1N";

		TestObject service = new TestObject();
		Dictionary<String,Object> serviceProps = new Hashtable<>();
		serviceProps.put(KEY, "initial");
		ServiceRegistration<TestObject> regInitial = context
				.registerService(TestObject.class, service, serviceProps);
		ServiceRegistration<TestObject> regHigher = null;

		Bundle tb17 = installer.installBundle(context.getBundle().getEntry("tb17.jar"), false);
		try {
			tb17.start();
			waitBundleStart();

			BaseService bsSR01 = getBaseService(TB17_SR01);
			BaseService bsSR11 = getBaseService(TB17_SR11);
			BaseService bsSR0N = getBaseService(TB17_SR0N);
			BaseService bsSR1N = getBaseService(TB17_SR1N);
			assertThat(bsSR01).as("Provided service of " + TB17_SR01
					+ " should be available").isNotNull();
			assertThat(bsSR11).as("Provided service of " + TB17_SR11
					+ " should be available").isNotNull();
			assertThat(bsSR0N).as("Provided service of " + TB17_SR0N
					+ " should be available").isNotNull();
			assertThat(bsSR1N).as("Provided service of " + TB17_SR1N
					+ " should be available").isNotNull();

			Object idSR01 = bsSR01.getProperties().get(IDENTITY);
			Object idSR11 = bsSR11.getProperties().get(IDENTITY);
			Object idSR0N = bsSR0N.getProperties().get(IDENTITY);
			Object idSR1N = bsSR1N.getProperties().get(IDENTITY);

			assertThat(bsSR01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR01/initial");
			assertThat(bsSR11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR11/initial");
			assertThat(bsSR0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR0N/initial");
			assertThat(bsSR1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR1N/initial");

			serviceProps.put(KEY, "higher");
			serviceProps.put(Constants.SERVICE_RANKING, Integer.valueOf(100));
			regHigher = context.registerService(
					TestObject.class, service, serviceProps);
			settle(SLEEP * 3);

			/* reacquire since they may have been reactivated */
			bsSR01 = getBaseService(TB17_SR01);
			bsSR11 = getBaseService(TB17_SR11);
			bsSR0N = getBaseService(TB17_SR0N);
			bsSR1N = getBaseService(TB17_SR1N);

			assertThat(bsSR01
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idSR01);
			assertThat(bsSR01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR01/initial");
			assertThat(bsSR11
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idSR11);
			assertThat(bsSR11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR11/initial");
			assertThat(bsSR0N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idSR0N);
			assertThat(bsSR0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR0N/initial");
			assertThat(bsSR1N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idSR1N);
			assertThat(bsSR1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSR1N/initial");

		}
		finally {
			tb17.uninstall();
			if (regHigher != null) {
				regHigher.unregister();
			}
			if (regInitial != null) {
				regInitial.unregister();
			}
		}
	}

	@Test
	public void testReferencePolicyOptionStaticGreedy() throws Exception {
		final String KEY = TEST_CASE_ROOT + ".tb17.serviceproperty";
		final String IDENTITY = TEST_CASE_ROOT + ".tb17.identity";
		final String TB17_SG01 = TEST_CASE_ROOT + ".tb17.SG01";
		final String TB17_SG11 = TEST_CASE_ROOT + ".tb17.SG11";
		final String TB17_SG0N = TEST_CASE_ROOT + ".tb17.SG0N";
		final String TB17_SG1N = TEST_CASE_ROOT + ".tb17.SG1N";

		TestObject service = new TestObject();
		Dictionary<String,Object> serviceProps = new Hashtable<>();
		serviceProps.put(KEY, "initial");
		ServiceRegistration<TestObject> regInitial = context
				.registerService(TestObject.class, service, serviceProps);
		ServiceRegistration<TestObject> regHigher = null;

		Bundle tb17 = installer.installBundle(context.getBundle().getEntry("tb17.jar"), false);
		try {
			tb17.start();
			waitBundleStart();

			BaseService bsSG01 = getBaseService(TB17_SG01);
			BaseService bsSG11 = getBaseService(TB17_SG11);
			BaseService bsSG0N = getBaseService(TB17_SG0N);
			BaseService bsSG1N = getBaseService(TB17_SG1N);
			assertThat(bsSG01).as("Provided service of " + TB17_SG01
					+ " should be available").isNotNull();
			assertThat(bsSG11).as("Provided service of " + TB17_SG11
					+ " should be available").isNotNull();
			assertThat(bsSG0N).as("Provided service of " + TB17_SG0N
					+ " should be available").isNotNull();
			assertThat(bsSG1N).as("Provided service of " + TB17_SG1N
					+ " should be available").isNotNull();

			Object idSG01 = bsSG01.getProperties().get(IDENTITY);
			Object idSG11 = bsSG11.getProperties().get(IDENTITY);
			Object idSG0N = bsSG0N.getProperties().get(IDENTITY);
			Object idSG1N = bsSG1N.getProperties().get(IDENTITY);

			assertThat(bsSG01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG01/initial");
			assertThat(bsSG11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG11/initial");
			assertThat(bsSG0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG0N/initial");
			assertThat(bsSG1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG1N/initial");

			serviceProps.put(KEY, "higher");
			serviceProps.put(Constants.SERVICE_RANKING, Integer.valueOf(100));
			regHigher = context.registerService(
					TestObject.class, service, serviceProps);
			settle(SLEEP * 3);

			/* reacquire since they may have been reactivated */
			bsSG01 = getBaseService(TB17_SG01);
			bsSG11 = getBaseService(TB17_SG11);
			bsSG0N = getBaseService(TB17_SG0N);
			bsSG1N = getBaseService(TB17_SG1N);

			assertThat(idSG01.equals(bsSG01.getProperties().get(IDENTITY))).as("component not reactivated").isFalse();
			assertThat(bsSG01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG01/higher");
			assertThat(idSG11.equals(bsSG11.getProperties().get(IDENTITY))).as("component not reactivated").isFalse();
			assertThat(bsSG11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG11/higher");
			assertThat(idSG0N.equals(bsSG0N.getProperties().get(IDENTITY))).as("component not reactivated").isFalse();
			assertThat(bsSG0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG0N/higher/initial");
			assertThat(idSG1N.equals(bsSG1N.getProperties().get(IDENTITY))).as("component not reactivated").isFalse();
			assertThat(bsSG1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindSG1N/higher/initial");

		}
		finally {
			tb17.uninstall();
			if (regHigher != null) {
				regHigher.unregister();
			}
			if (regInitial != null) {
				regInitial.unregister();
			}
		}
	}

	@Test
	public void testReferencePolicyOptionDynamicReluctant() throws Exception {
		final String KEY = TEST_CASE_ROOT + ".tb17.serviceproperty";
		final String IDENTITY = TEST_CASE_ROOT + ".tb17.identity";
		final String TB17_DR01 = TEST_CASE_ROOT + ".tb17.DR01";
		final String TB17_DR11 = TEST_CASE_ROOT + ".tb17.DR11";
		final String TB17_DR0N = TEST_CASE_ROOT + ".tb17.DR0N";
		final String TB17_DR1N = TEST_CASE_ROOT + ".tb17.DR1N";

		TestObject service = new TestObject();
		Dictionary<String,Object> serviceProps = new Hashtable<>();
		serviceProps.put(KEY, "initial");
		ServiceRegistration<TestObject> regInitial = context
				.registerService(TestObject.class, service, serviceProps);
		ServiceRegistration<TestObject> regHigher = null;

		Bundle tb17 = installer.installBundle(context.getBundle().getEntry("tb17.jar"), false);
		try {
			tb17.start();
			waitBundleStart();

			BaseService bsDR01 = getBaseService(TB17_DR01);
			BaseService bsDR11 = getBaseService(TB17_DR11);
			BaseService bsDR0N = getBaseService(TB17_DR0N);
			BaseService bsDR1N = getBaseService(TB17_DR1N);
			assertThat(bsDR01).as("Provided service of " + TB17_DR01
					+ " should be available").isNotNull();
			assertThat(bsDR11).as("Provided service of " + TB17_DR11
					+ " should be available").isNotNull();
			assertThat(bsDR0N).as("Provided service of " + TB17_DR0N
					+ " should be available").isNotNull();
			assertThat(bsDR1N).as("Provided service of " + TB17_DR1N
					+ " should be available").isNotNull();

			Object idDR01 = bsDR01.getProperties().get(IDENTITY);
			Object idDR11 = bsDR11.getProperties().get(IDENTITY);
			Object idDR0N = bsDR0N.getProperties().get(IDENTITY);
			Object idDR1N = bsDR1N.getProperties().get(IDENTITY);

			assertThat(bsDR01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR01/initial");
			assertThat(bsDR11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR11/initial");
			assertThat(bsDR0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR0N/initial");
			assertThat(bsDR1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR1N/initial");

			serviceProps.put(KEY, "higher");
			serviceProps.put(Constants.SERVICE_RANKING, Integer.valueOf(100));
			regHigher = context.registerService(
					TestObject.class, service, serviceProps);
			settle(SLEEP * 3);

			/* reacquire since they may have been reactivated */
			bsDR01 = getBaseService(TB17_DR01);
			bsDR11 = getBaseService(TB17_DR11);
			bsDR0N = getBaseService(TB17_DR0N);
			bsDR1N = getBaseService(TB17_DR1N);

			assertThat(bsDR01
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDR01);
			assertThat(bsDR01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR01/initial");
			assertThat(bsDR11
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDR11);
			assertThat(bsDR11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR11/initial");
			assertThat(bsDR0N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDR0N);
			assertThat(bsDR0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR0N/higher/initial");
			assertThat(bsDR1N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDR1N);
			assertThat(bsDR1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDR1N/higher/initial");

		}
		finally {
			tb17.uninstall();
			if (regHigher != null) {
				regHigher.unregister();
			}
			if (regInitial != null) {
				regInitial.unregister();
			}
		}
	}

	@Test
	public void testReferencePolicyOptionDynamicGreedy() throws Exception {
		final String KEY = TEST_CASE_ROOT + ".tb17.serviceproperty";
		final String IDENTITY = TEST_CASE_ROOT + ".tb17.identity";
		final String TB17_DG01 = TEST_CASE_ROOT + ".tb17.DG01";
		final String TB17_DG11 = TEST_CASE_ROOT + ".tb17.DG11";
		final String TB17_DG0N = TEST_CASE_ROOT + ".tb17.DG0N";
		final String TB17_DG1N = TEST_CASE_ROOT + ".tb17.DG1N";

		TestObject service = new TestObject();
		Dictionary<String,Object> serviceProps = new Hashtable<>();
		serviceProps.put(KEY, "initial");
		ServiceRegistration<TestObject> regInitial = context
				.registerService(TestObject.class, service, serviceProps);
		ServiceRegistration<TestObject> regHigher = null;

		Bundle tb17 = installer.installBundle(context.getBundle().getEntry("tb17.jar"), false);
		try {
			tb17.start();
			waitBundleStart();

			BaseService bsDG01 = getBaseService(TB17_DG01);
			BaseService bsDG11 = getBaseService(TB17_DG11);
			BaseService bsDG0N = getBaseService(TB17_DG0N);
			BaseService bsDG1N = getBaseService(TB17_DG1N);
			assertThat(bsDG01).as("Provided service of " + TB17_DG01
					+ " should be available").isNotNull();
			assertThat(bsDG11).as("Provided service of " + TB17_DG11
					+ " should be available").isNotNull();
			assertThat(bsDG0N).as("Provided service of " + TB17_DG0N
					+ " should be available").isNotNull();
			assertThat(bsDG1N).as("Provided service of " + TB17_DG1N
					+ " should be available").isNotNull();

			Object idDG01 = bsDG01.getProperties().get(IDENTITY);
			Object idDG11 = bsDG11.getProperties().get(IDENTITY);
			Object idDG0N = bsDG0N.getProperties().get(IDENTITY);
			Object idDG1N = bsDG1N.getProperties().get(IDENTITY);

			assertThat(bsDG01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG01/initial");
			assertThat(bsDG11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG11/initial");
			assertThat(bsDG0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG0N/initial");
			assertThat(bsDG1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG1N/initial");

			serviceProps.put(KEY, "higher");
			serviceProps.put(Constants.SERVICE_RANKING, Integer.valueOf(100));
			regHigher = context.registerService(
					TestObject.class, service, serviceProps);
			settle(SLEEP * 3);

			/* reacquire since they may have been reactivated */
			bsDG01 = getBaseService(TB17_DG01);
			bsDG11 = getBaseService(TB17_DG11);
			bsDG0N = getBaseService(TB17_DG0N);
			bsDG1N = getBaseService(TB17_DG1N);

			assertThat(bsDG01
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDG01);
			assertThat(bsDG01.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbindDG01/higher");
			assertThat(bsDG11
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDG11);
			assertThat(bsDG11.getProperties().get(KEY)).as("service property incorrect").isEqualTo("unbindDG11/higher");
			assertThat(bsDG0N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDG0N);
			assertThat(bsDG0N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG0N/higher/initial");
			assertThat(bsDG1N
					.getProperties().get(IDENTITY)).as("component reactivated").isEqualTo(idDG1N);
			assertThat(bsDG1N.getProperties().get(KEY)).as("service property incorrect").isEqualTo("bindDG1N/higher/initial");

		}
		finally {
			tb17.uninstall();
			if (regHigher != null) {
				regHigher.unregister();
			}
			if (regInitial != null) {
				regInitial.unregister();
			}
		}
	}


	/**
	 * Searches for component with name componentName which provides
	 * BaseService. Returns value of its "config.base.data" property.
	 *
	 * @param componentName
	 *            - the name of the component to get data
	 * @return the value of property "config.base.data", provided by
	 *         BaseService.getProperties(). Returned value is -1 when component
	 *         which provides BaseService and has specified name is not
	 *         activated. Returned value is 0 when component with specified name
	 *         is active but doesn't have property "config.base.data".
	 */
	private long getBaseConfigData(String componentName) {
		BaseService s = getBaseService(componentName);
		return getBaseConfigData(s);
	}

	private long getBaseConfigData(BaseService s) {
		Dictionary<String,Object> props = null;
		long value = -1L;
		if (s != null) {
			value = 0;
			if ((props = s.getProperties()) != null) {
				Object prop = props.get("config.base.data");
				if (prop instanceof Integer) {
					value = ((Integer) prop).longValue();
				}
				else
					if (prop instanceof Long) {
						value = ((Long) prop).longValue();

				}
			}
		}
		return value;
	}

	private BaseService getBaseService(String componentName) {
		Object[] services = trackerBaseService.getServices();
		if (services == null) {
			return null;
		}
		for (int i = 0; i < services.length; i++) {
			if (services[i] instanceof BaseService) {
				BaseService s = (BaseService) services[i];
				Dictionary<String,Object> props = s.getProperties();
				if (props != null
						&& ((String) props
								.get(ComponentConstants.COMPONENT_NAME))
								.equals(componentName)) {
					return s;
				}
			}
		}
		return null;
	}

	private boolean checkAvailability(String service) {
		BundleContext bc = context;
		ServiceReference< ? > ref = bc.getServiceReference(service);
		return ref != null;
	}

	private static boolean checkDataBits(long bits, long data) {
		return bits == (bits & data);
	}

	public void sleep0(long millisToSleep) {
		long start = System.currentTimeMillis();
		do {
			try {
				settle(50);
			} catch (InterruptedException e) {
			}
		} while (System.currentTimeMillis() - start < millisToSleep);
	}

	private void waitBundleStart() {
		if (!synchronousBuild) {
			sleep0(2 * SLEEP);
		}
	}

	@Test
	public void testScopedServiceSingleton130() throws Exception {
		Bundle tb19 = installer.installBundle(context.getBundle().getEntry("tb19.jar"), false);
		assertThat(tb19).as("tb19 failed to install").isNotNull();
		
		try {
			tb19.start();

			Filter baseFilter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName()
					+ ")(" + ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb19.Singleton))");
			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb19.SingletonReceiver))");
			ServiceTracker<BaseService, BaseService> baseTracker = new ServiceTracker<BaseService, BaseService>(
					context, baseFilter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiverTracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiverFilter, null);
			try {
				baseTracker.open();
				receiverTracker.open();
				BaseService s = baseTracker.waitForService(SLEEP * 3);
				assertThat(s).as("missing service").isNotNull();
				ServiceReceiver<BaseService> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				assertThat(r.getServices().get(0)).as("singleton service is not the same object").isSameAs(s);
			}
			finally {
				baseTracker.close();
				receiverTracker.close();
			}
		}
		finally {
			tb19.uninstall();
		}
	}

	@Test
	public void testScopedServiceBundle130() throws Exception {
		Bundle tb19 = installer.installBundle(context.getBundle().getEntry("tb19.jar"), false);
		assertThat(tb19).as("tb19 failed to install").isNotNull();

		try {
			tb19.start();

			Filter baseFilter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName()
					+ ")(" + ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb19.Bundle))");
			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb19.BundleReceiver))");
			ServiceTracker<BaseService, BaseService> baseTracker = new ServiceTracker<BaseService, BaseService>(
					context, baseFilter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiverTracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiverFilter, null);
			try {
				baseTracker.open();
				receiverTracker.open();
				BaseService s = baseTracker.waitForService(SLEEP * 3);
				assertThat(s).as("missing service").isNotNull();
				ServiceReceiver<BaseService> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				assertThat(r.getServices().get(0)).as("bundle service is the same object").isNotSameAs(s);
				assertThat(r.getServicesProperies().get(0).get(Constants.SERVICE_SCOPE)).as("scope not bundle").isEqualTo(Constants.SCOPE_BUNDLE);
				assertThat(baseTracker.getServiceReference().getProperty(Constants.SERVICE_SCOPE)).as("scope not bundle").isEqualTo(Constants.SCOPE_BUNDLE);
			}
			finally {
				baseTracker.close();
				receiverTracker.close();
			}
		}
		finally {
			tb19.uninstall();
		}
	}

	@Test
	public void testScopedServicePrototype130() throws Exception {
		Bundle tb19 = installer.installBundle(context.getBundle().getEntry("tb19.jar"), false);
		assertThat(tb19).as("tb19 failed to install").isNotNull();

		try {
			tb19.start();

			Filter baseFilter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb19.Prototype))");
			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb19.PrototypeReceiver))");
			ServiceTracker<BaseService, BaseService> baseTracker = new ServiceTracker<BaseService, BaseService>(
					context, baseFilter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiverTracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiverFilter, null);
			try {
				baseTracker.open();
				receiverTracker.open();
				BaseService s = baseTracker.waitForService(SLEEP * 3);
				assertThat(s).as("missing service").isNotNull();
				ServiceReceiver<BaseService> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				assertThat(r.getServices().get(0)).as("bundle service is the same object").isNotSameAs(s);
				assertThat(r.getServicesProperies().get(0).get(Constants.SERVICE_SCOPE)).as("scope not prototype").isEqualTo(Constants.SCOPE_PROTOTYPE);
				assertThat(baseTracker.getServiceReference().getProperty(Constants.SERVICE_SCOPE)).as("scope not prototype").isEqualTo(Constants.SCOPE_PROTOTYPE);
				ServiceObjects<BaseService> so = context.getServiceObjects(baseTracker.getServiceReference());
				List<BaseService> list = new ArrayList<BaseService>(20);
				for (int i = 0; i < 20; i++) {
					BaseService o = so.getService();
					assertThat(o).as("prototype same as tracked service").isNotSameAs(s);
					assertThat(o).as("prototype same as bound service").isNotSameAs(r.getServices().get(0));
					for (int j = 0; j < list.size(); j++) {
						assertThat(o).as("duplicate prototype service object").isNotSameAs(list.get(j));
					}
					list.add(o);
				}
			}
			finally {
				baseTracker.close();
				receiverTracker.close();
			}
		}
		finally {
			tb19.uninstall();
		}
	}

	@Test
	public void testScopedReferenceBundle130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.BundleReceiverPrototype1))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.BundleReceiverPrototype2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2).isNotSameAs(r1);
				BaseService s = r1.getServices().get(0);
				for (BaseService o : r1.getServices()) {
					assertThat(o).as("service not same").isSameAs(s);
				}
				for (BaseService o : r2.getServices()) {
					assertThat(o).as("service not same").isSameAs(s);
				}
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testScopedReferencePrototypeBundle130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeReceiverBundle1))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeReceiverBundle2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2).isNotSameAs(r1);
				BaseService s = r1.getServices().get(0);
				for (BaseService o : r1.getServices()) {
					assertThat(o).as("service not same").isSameAs(s);
				}
				for (BaseService o : r2.getServices()) {
					assertThat(o).as("service not same").isSameAs(s);
				}
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testScopedReferencePrototypePrototype130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeReceiverPrototype1))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeReceiverPrototype2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2).isNotSameAs(r1);
				BaseService s1 = r1.getServices().get(0);
				BaseService s2 = r2.getServices().get(0);
				assertThat(s2).as("two components share same instance").isNotSameAs(s1);
				for (BaseService o : r1.getServices()) {
					assertThat(o).as("component does not share same instance").isSameAs(s1);
				}
				for (BaseService o : r2.getServices()) {
					assertThat(o).as("component does not share same instance").isSameAs(s2);
				}
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testScopedReferencePrototypeRequiredPrototype130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeRequiredReceiverPrototype1))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeRequiredReceiverPrototype2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2).isNotSameAs(r1);
				BaseService s1 = r1.getServices().get(0);
				BaseService s2 = r2.getServices().get(0);
				assertThat(s2).as("two components share same instance").isNotSameAs(s1);
				for (BaseService o : r1.getServices()) {
					assertThat(o).as("component does not share same instance").isSameAs(s1);
				}
				for (BaseService o : r2.getServices()) {
					assertThat(o).as("component does not share same instance").isSameAs(s2);
				}
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testScopedReferencePrototypeRequiredSingleton130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeRequiredReceiverSingleton))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiverTracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiverFilter, null);
			try {
				receiverTracker.open();
				ServiceReceiver<BaseService> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("receiver should not have been registered").isNull();
			}
			finally {
				receiverTracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testScopedReferencePrototypeRequiredBundle130() throws Exception {
		Bundle tb20 = installer.installBundle(context.getBundle().getEntry("tb20.jar"), false);
		assertThat(tb20).as("tb20 failed to install").isNotNull();

		try {
			tb20.start();

			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb20.PrototypeRequiredReceiverBundle))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiverTracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiverFilter, null);
			try {
				receiverTracker.open();
				ServiceReceiver<BaseService> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("receiver should not have been registered").isNull();
			}
			finally {
				receiverTracker.close();
			}
		}
		finally {
			tb20.uninstall();
		}
	}

	@Test
	public void testServiceComponentRuntimeDescription() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Collection<ComponentDescriptionDTO> descriptions = scr.getComponentDescriptionDTOs(tb1, tb2, tb3);
		assertThat(descriptions).as("null descriptions").isNotNull();
		assertThat(descriptions.isEmpty()).as("descriptions empty").isFalse();
		assertThat(scr.getComponentDescriptionDTOs().size()).as("no args result size mismatch").isEqualTo(descriptions.size());
		ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb1,
				"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl");
		assertThat(description1).as("null description1").isNotNull();
		ComponentDescriptionDTO expected1 = newComponentDescriptionDTO(
				"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl",
				newBundleDTO(tb1), null, "bundle",
				"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl", true, false,
				new String[] {
						"org.osgi.test.cases.component.service.ServiceProvider"
				}, Collections.<String, Object> emptyMap(),
				new ReferenceDTO[] {}, "activate", "deactivate", null, "optional",
				new String[] {
						"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl"
				}, null, new String[0], 0);
		assertThat(description1)
				.usingRecursiveComparison()
				.isEqualTo(expected1);
		boolean found = false;
		for (ComponentDescriptionDTO d : descriptions) {
			if (d.bundle.id != description1.bundle.id)
				continue;
			if (!d.name.equals(description1.name))
				continue;
			assertThat(d)
					.usingRecursiveComparison()
					.isEqualTo(description1);
			found = true;
			break;
		}
		assertThat(found).as("description1 not found").isTrue();

		ComponentDescriptionDTO description3 = scr.getComponentDescriptionDTO(tb3,
				"org.osgi.test.cases.component.tb3.ServiceConsumerEvent");
		assertThat(description3).as("null description3").isNotNull();
		Map<String, Object> properties3 = new HashMap<String, Object>();
		properties3.put("serviceProvider.target",
				"(component.name=org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl)");
		ComponentDescriptionDTO expected3 = newComponentDescriptionDTO(
				"org.osgi.test.cases.component.tb3.ServiceConsumerEvent",
				newBundleDTO(tb3), null, "singleton",
				"org.osgi.test.cases.component.tb3.impl.ServiceConsumerEventImpl", true, false,
				new String[] {"org.osgi.test.cases.component.tb3.ServiceConsumerEvent"}, properties3,
				new ReferenceDTO[] {
						newReferenceDTO("serviceProvider",
								"org.osgi.test.cases.component.service.ServiceProvider",
								"1..1", "static", "reluctant",
								"(component.name=org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl)",
								"bindServiceProvider", "unbindServiceProvider",
								null, null, null, "bundle", null, null),
						newReferenceDTO("namedService",
								"org.osgi.test.cases.component.tb4.NamedService",
								"0..n",
								"dynamic", "reluctant", null, "bindObject", "unbindObject", null, null, null,
								"bundle", null, null)
				},
				"activate", "deactivate", null, "optional",
				new String[] {
						"org.osgi.test.cases.component.tb3.ServiceConsumerEvent"
				}, null, new String[0], 0);
		assertThat(description3)
				.usingRecursiveComparison()
				.isEqualTo(expected3);
		found = false;
		for (ComponentDescriptionDTO d : descriptions) {
			if (d.bundle.id != description3.bundle.id)
				continue;
			if (!d.name.equals(description3.name))
				continue;
			assertThat(d)
					.usingRecursiveComparison()
					.isEqualTo(description3);
			found = true;
			break;
		}
		assertThat(found).as("description1 not found").isTrue();
	}

	@Test
	public void testServiceComponentRuntimeConfiguration() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb1,
				"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl");
		assertThat(description1).as("null description1").isNotNull();
		Collection<ComponentConfigurationDTO> configurations1 = scr.getComponentConfigurationDTOs(description1);
		assertThat(configurations1).as("null configurations1").isNotNull();
		assertThat(configurations1).as("wrong number of configurations").hasSize(1);
		ComponentConfigurationDTO configuration1 = configurations1.iterator().next();
		assertThat(configuration1).as("null configuration").isNotNull();
		assertThat(configuration1.description)
				.usingRecursiveComparison()
				.isEqualTo(description1);
		assertThat(configuration1.state).as("wrong state in configuration").isEqualTo(ComponentConfigurationDTO.ACTIVE);
		assertThat(configuration1.satisfiedReferences.length).as("wrong number of satisfiedReferences").isEqualTo(1);
		assertThat(configuration1.unsatisfiedReferences.length).as("wrong number of unsatisfiedReferences").isEqualTo(0);
		assertThat(configuration1.properties.get(ComponentConstants.COMPONENT_NAME)).as("wrong component name").isEqualTo(description1.name);
		assertThat(configuration1.properties.containsKey(ComponentConstants.COMPONENT_ID)).as("configuration missing component.id property").isTrue();
		assertThat(configuration1.failure).isNull();
		ServiceReferenceDTO[] tb1SRs = tb1.adapt(ServiceReferenceDTO[].class);
		assertThat(tb1SRs).hasSize(1);
		assertThat(configuration1.service)
				.usingRecursiveComparison()
				.isEqualTo(tb1SRs[0]);

		ComponentDescriptionDTO description3 = scr.getComponentDescriptionDTO(tb3,
				"org.osgi.test.cases.component.tb3.ServiceConsumerEvent");
		assertThat(description3).as("null description3").isNotNull();
		Collection<ComponentConfigurationDTO> configurations3 = scr.getComponentConfigurationDTOs(description3);
		assertThat(configurations3).as("null configurations3").isNotNull();
		assertThat(configurations3).as("wrong number of configurations").hasSize(1);
		ComponentConfigurationDTO configuration3 = configurations3.iterator().next();
		assertThat(configuration3).as("null configuration").isNotNull();
		assertThat(configuration3.description)
				.usingRecursiveComparison()
				.isEqualTo(description3);
		assertThat(configuration3.state).as("wrong state in configuration").isEqualTo(ComponentConfigurationDTO.ACTIVE);
		assertThat(configuration3.satisfiedReferences.length).as("wrong number of satisfiedReferences").isEqualTo(3);
		assertThat(configuration3.unsatisfiedReferences.length).as("wrong number of unsatisfiedReferences").isEqualTo(0);
		assertThat(configuration3.properties.get(ComponentConstants.COMPONENT_NAME)).as("wrong component name").isEqualTo(description3.name);
		assertThat(configuration3.properties.containsKey(ComponentConstants.COMPONENT_ID)).as("configuration missing component.id property").isTrue();
		assertThat(configuration3.failure).isNull();
		ServiceReferenceDTO[] tb3SRs = tb3.adapt(ServiceReferenceDTO[].class);
		assertThat(tb3SRs).hasSize(1);
		assertThat(configuration3.service)
				.usingRecursiveComparison()
				.isEqualTo(tb3SRs[0]);
	}

	@Test
	public void testServiceComponentRuntimeEnablement() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb1,
				"org.osgi.test.cases.component.tb1.impl.ServiceProviderImpl");
		assertThat(description1).as("null description1").isNotNull();

		Collection<ComponentConfigurationDTO> configurations1 = scr.getComponentConfigurationDTOs(description1);
		assertThat(configurations1).as("null configurations1").isNotNull();
		assertThat(configurations1).as("wrong number of configurations").hasSize(1);
		ComponentConfigurationDTO configuration1 = configurations1.iterator().next();
		assertThat(configuration1).as("null configuration").isNotNull();
		assertThat(configuration1.state).as("wrong state in configuration").isEqualTo(ComponentConfigurationDTO.ACTIVE);

		assertThat(scr.isComponentEnabled(description1)).as("description is not enabled").isTrue();
		Promise<Void> p = scr.disableComponent(description1);
		p.getValue(); // wait for state change to complete
		assertThat(scr.isComponentEnabled(description1)).as("description is enabled").isFalse();
		configurations1 = scr.getComponentConfigurationDTOs(description1);
		assertThat(configurations1).as("null configurations1").isNotNull();
		assertThat(configurations1).as("wrong number of configurations").hasSize(0);

		p = scr.enableComponent(description1);
		p.getValue(); // wait for state change to complete
		assertThat(scr.isComponentEnabled(description1)).as("description is not enabled").isTrue();
		configurations1 = scr.getComponentConfigurationDTOs(description1);
		assertThat(configurations1).as("null configurations1").isNotNull();
		assertThat(configurations1).as("wrong number of configurations").hasSize(1);
		assertThat(configuration1.state).as("wrong state in configuration").isEqualTo(ComponentConfigurationDTO.ACTIVE);
	}

	@Test
	public void testMinimumCardinality01130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb21 = installer.installBundle(context.getBundle().getEntry("tb21.jar"), false);
		assertThat(tb21).as("tb21 failed to install").isNotNull();

		try {
			tb21.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent1");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();
			ComponentDescriptionDTO description2 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent2");
			assertThat(description2).as("null description2").isNotNull();
			assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver01NoMinimum))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver01Minimum1))");
			Filter receiver3Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver01Minimum2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver3Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver3Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				receiver3Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices()).as("receiver1 has services").isEmpty();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("receiver2 available").isNull();
				ServiceReceiver<BaseService> r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices()).as("receiver3 has services").isEmpty();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);

				p = scr.enableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is not enabled").isTrue();

				r1 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);

				p = scr.disableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

				r1 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);

				p = scr.disableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices()).as("receiver1 has services").isEmpty();
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("receiver2 available").isNull();
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices()).as("receiver3 has services").isEmpty();
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
				receiver3Tracker.close();
			}
		}
		finally {
			tb21.uninstall();
		}
	}

	@Test
	public void testMinimumCardinality0n130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb21 = installer.installBundle(context.getBundle().getEntry("tb21.jar"), false);
		assertThat(tb21).as("tb21 failed to install").isNotNull();

		try {
			tb21.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent1");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();
			ComponentDescriptionDTO description2 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent2");
			assertThat(description2).as("null description2").isNotNull();
			assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver0nNoMinimum))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver0nMinimum1))");
			Filter receiver3Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver0nMinimum2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver3Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver3Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				receiver3Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices()).as("receiver1 has services").isEmpty();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("receiver2 available").isNull();
				ServiceReceiver<BaseService> r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("receiver3 available").isNull();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("receiver3 available").isNull();

				p = scr.enableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has noservices").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 2 service").isEqualTo(2);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 2 service").isEqualTo(2);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 2 service").isEqualTo(2);

				p = scr.disableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has noservices").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("receiver3 available").isNull();

			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
				receiver3Tracker.close();
			}
		}
		finally {
			tb21.uninstall();
		}
	}

	@Test
	public void testMinimumCardinality11130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb21 = installer.installBundle(context.getBundle().getEntry("tb21.jar"), false);
		assertThat(tb21).as("tb21 failed to install").isNotNull();

		try {
			tb21.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent1");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();
			ComponentDescriptionDTO description2 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent2");
			assertThat(description2).as("null description2").isNotNull();
			assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver11NoMinimum))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver11Minimum0))");
			Filter receiver3Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver11Minimum1))");
			Filter receiver4Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver11Minimum2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver3Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver3Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver4Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver4Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				receiver3Tracker.open();
				receiver4Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("receiver1 available").isNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("receiver2 available").isNull();
				ServiceReceiver<BaseService> r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("receiver3 available").isNull();
				ServiceReceiver<BaseService> r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("receiver4 available").isNull();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);
				r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("missing receiver4").isNotNull();
				assertThat(r4.getServices().isEmpty()).as("receiver4 has no services").isFalse();
				assertThat(r4.getServices().size()).as("receiver4 does not have exactly 1 service").isEqualTo(1);

				p = scr.enableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is not enabled").isTrue();
				r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("missing receiver4").isNotNull();
				assertThat(r4.getServices().isEmpty()).as("receiver4 has no services").isFalse();
				assertThat(r4.getServices().size()).as("receiver4 does not have exactly 1 service").isEqualTo(1);
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
				receiver3Tracker.close();
				receiver4Tracker.close();
			}
		}
		finally {
			tb21.uninstall();
		}
	}

	@Test
	public void testMinimumCardinality1n130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb21 = installer.installBundle(context.getBundle().getEntry("tb21.jar"), false);
		assertThat(tb21).as("tb21 failed to install").isNotNull();

		try {
			tb21.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent1");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();
			ComponentDescriptionDTO description2 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent2");
			assertThat(description2).as("null description2").isNotNull();
			assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

			Filter receiver1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver1nNoMinimum))");
			Filter receiver2Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver1nMinimum0))");
			Filter receiver3Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver1nMinimum1))");
			Filter receiver4Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb21.Receiver1nMinimum2))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver2Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver2Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver3Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver3Filter, null);
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver4Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver4Filter, null);
			try {
				receiver1Tracker.open();
				receiver2Tracker.open();
				receiver3Tracker.open();
				receiver4Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("receiver1 available").isNull();
				ServiceReceiver<BaseService> r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("receiver2 available").isNull();
				ServiceReceiver<BaseService> r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("receiver3 available").isNull();
				ServiceReceiver<BaseService> r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("receiver4 available").isNull();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);
				r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("receiver4 available").isNull();

				p = scr.enableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 2 service").isEqualTo(2);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 2 service").isEqualTo(2);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 2 service").isEqualTo(2);
				r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("missing receiver4").isNotNull();
				assertThat(r4.getServices().isEmpty()).as("receiver4 has no services").isFalse();
				assertThat(r4.getServices().size()).as("receiver4 does not have exactly 2 service").isEqualTo(2);

				p = scr.disableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 1 service").isEqualTo(1);
				r2 = receiver2Tracker.waitForService(SLEEP * 3);
				assertThat(r2).as("missing receiver2").isNotNull();
				assertThat(r2.getServices().isEmpty()).as("receiver2 has no services").isFalse();
				assertThat(r2.getServices().size()).as("receiver2 does not have exactly 1 service").isEqualTo(1);
				r3 = receiver3Tracker.waitForService(SLEEP * 3);
				assertThat(r3).as("missing receiver3").isNotNull();
				assertThat(r3.getServices().isEmpty()).as("receiver3 has no services").isFalse();
				assertThat(r3.getServices().size()).as("receiver3 does not have exactly 1 service").isEqualTo(1);
				r4 = receiver4Tracker.waitForService(SLEEP * 3);
				assertThat(r4).as("receiver4 available").isNull();
			}
			finally {
				receiver1Tracker.close();
				receiver2Tracker.close();
				receiver3Tracker.close();
				receiver4Tracker.close();
			}
		}
		finally {
			tb21.uninstall();
		}
	}

	@Test
	public void testMinimumCardinality110() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb21 = installer.installBundle(context.getBundle().getEntry("tb21.jar"), false);
		assertThat(tb21).as("tb21 failed to install").isNotNull();

		try {
			tb21.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent1");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();
			ComponentDescriptionDTO description2 = scr.getComponentDescriptionDTO(tb21,
					"org.osgi.test.cases.component.tb21.ServiceComponent2");
			assertThat(description2).as("null description2").isNotNull();
			assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

			Filter receiver1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ServiceReceiver.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb21.Receiverv11))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			try {
				receiver1Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("receiver1 available").isNull();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("receiver1 available").isNull();

				p = scr.enableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				assertThat(r1.getServices().isEmpty()).as("receiver1 has no services").isFalse();
				assertThat(r1.getServices().size()).as("receiver1 does not have exactly 2 service").isEqualTo(2);

				p = scr.disableComponent(description2);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description2)).as("description2 is enabled").isFalse();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("receiver4 available").isNull();
			}
			finally {
				receiver1Tracker.close();
			}
		}
		finally {
			tb21.uninstall();
		}
	}

	@Test
	public void testComparableMap130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb22 = installer.installBundle(context.getBundle().getEntry("tb22.jar"), false);
		assertThat(tb22).as("tb22 failed to install").isNotNull();

		try {
			tb22.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb22,
					"org.osgi.test.cases.component.tb22.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter receiver1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ServiceReceiver.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb22.MapReceiver))");
			ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>> receiver1Tracker = new ServiceTracker<ServiceReceiver<BaseService>, ServiceReceiver<BaseService>>(
					context, receiver1Filter, null);
			try {
				receiver1Tracker.open();
				ServiceReceiver<BaseService> r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				List<Map<String, Object>> props = r1.getServicesProperies();
				assertThat(props.isEmpty()).as("props empty").isFalse();
				assertThat(props.size()).as("props size not 1").isEqualTo(1);
				Map<String, Object> p1 = props.get(0);
				assertThat(p1).as("p1 null").isNotNull();
				assertThat(p1 instanceof Comparable).as("p1 not Comparable").isTrue();
				assertThat(p1.get(Constants.SERVICE_RANKING)).as("wrong ranking").isEqualTo(Integer.valueOf(1));

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				r1 = receiver1Tracker.waitForService(SLEEP * 3);
				assertThat(r1).as("missing receiver1").isNotNull();
				props = r1.getServicesProperies();
				assertThat(props.isEmpty()).as("props empty").isFalse();
				assertThat(props.size()).as("props size not 2").isEqualTo(2);
				p1 = props.get(0);
				assertThat(p1).as("p1 null").isNotNull();
				assertThat(p1 instanceof Comparable).as("p1 not Comparable").isTrue();
				assertThat(p1.get(Constants.SERVICE_RANKING)).as("wrong ranking").isEqualTo(Integer.valueOf(10));
				Map<String, Object> p2 = props.get(1);
				assertThat(p2).as("p2 null").isNotNull();
				assertThat(p2 instanceof Comparable).as("p2 not Comparable").isTrue();
				assertThat(p2.get(Constants.SERVICE_RANKING)).as("wrong ranking").isEqualTo(Integer.valueOf(1));
			}
			finally {
				receiver1Tracker.close();
			}
		}
		finally {
			tb22.uninstall();
		}
	}

	@Test
	public void testConfigurationSinglePID130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String PID = PID_ROOT + ".SinglePID";

		Configuration config = cm.getConfiguration(PID, null);
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config");
		config.update(props);

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		try {
			tb23.start();

			Filter base1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb23.SinglePID))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				base1Tracker.open();
				BaseService b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID);
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationSinglePIDFactory130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String PID1 = PID_ROOT + ".SinglePID";
		final String PID2 = PID_ROOT + ".SinglePIDFactory";

		Configuration config = cm.getConfiguration(PID1, null);
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config1");
		props.put(PID1, "config1");
		config.update(props);

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ComponentFactory.class.getName() + ")(" + ComponentConstants.COMPONENT_FACTORY
					+ "=org.osgi.test.cases.component.tb23.SinglePIDFactory))");
			ServiceTracker<ComponentFactory<BaseService>,ComponentFactory<BaseService>> base1Tracker = new ServiceTracker<>(
					context, base1Filter, null);
			try {
				base1Tracker.open();
				ComponentFactory<BaseService> f1 = base1Tracker
						.waitForService(SLEEP * 3);
				assertThat(f1).as("missing factory1").isNotNull();
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "factory");
				props.put(PID2, "factory");
				ComponentInstance<BaseService> i1 = f1.newInstance(props);
				assertThat(i1).as("missing ComponentInstance").isNotNull();
				BaseService b1 = i1.getInstance();
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("factory");
				assertThat(props.get(PID1)).as("configurations not merged").isEqualTo("config1");
				assertThat(props.get(PID2)).as("configurations not merged").isEqualTo("factory");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationMultiplePIDs130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String PID1 = PID_ROOT + ".MultiplePID1";
		final String PID2 = PID_ROOT + ".MultiplePID2";

		Configuration config = cm.getConfiguration(PID1, null);
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config1");
		props.put(PID1, "config1");
		config.update(props);
		config = cm.getConfiguration(PID2, null);
		props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config2");
		props.put(PID2, "config2");
		config.update(props);

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		try {
			tb23.start();

			Filter base1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb23.MultiplePIDs))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				base1Tracker.open();
				BaseService b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(PID1)).as("configurations not merged").isEqualTo("config1");
				assertThat(props.get(PID2)).as("configurations not merged").isEqualTo("config2");
				@SuppressWarnings("unchecked")
				Collection<String> pids = (Collection<String>) props.get(Constants.SERVICE_PID);
				assertThat(pids).as("pids collection wrong").isEqualTo(Arrays.asList(PID1, PID2));
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationMultiplePIDsFactory130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String PID1 = PID_ROOT + ".MultiplePID1";
		final String PID2 = PID_ROOT + ".MultiplePID2";
		final String PID3 = PID_ROOT + ".MultiplePIDFactory";

		Configuration config = cm.getConfiguration(PID1, null);
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config1");
		props.put(PID1, "config1");
		config.update(props);
		config = cm.getConfiguration(PID2, null);
		props = new Hashtable<String, Object>();
		props.put(PID_ROOT, "config2");
		props.put(PID2, "config2");
		config.update(props);

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ComponentFactory.class.getName() + ")(" + ComponentConstants.COMPONENT_FACTORY
					+ "=org.osgi.test.cases.component.tb23.MultiplePIDsFactory))");
			ServiceTracker<ComponentFactory<BaseService>,ComponentFactory<BaseService>> base1Tracker = new ServiceTracker<>(
					context, base1Filter, null);
			try {
				base1Tracker.open();
				ComponentFactory<BaseService> f1 = base1Tracker
						.waitForService(SLEEP * 3);
				assertThat(f1).as("missing factory1").isNotNull();
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "factory");
				props.put(PID3, "factory");
				ComponentInstance<BaseService> i1 = f1.newInstance(props);
				assertThat(i1).as("missing ComponentInstance").isNotNull();
				BaseService b1 = i1.getInstance();
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("factory");
				assertThat(props.get(PID1)).as("configurations not merged").isEqualTo("config1");
				assertThat(props.get(PID2)).as("configurations not merged").isEqualTo("config2");
				assertThat(props.get(PID3)).as("configurations not merged").isEqualTo("factory");
				@SuppressWarnings("unchecked")
				Collection<String> pids = (Collection<String>) props.get(Constants.SERVICE_PID);
				assertThat(pids).as("pids collection wrong").isEqualTo(Arrays.asList(PID1, PID2));
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationTargetedPIDRequiredNoModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		final String bsn = tb23.getSymbolicName();
		final String version = tb23.getVersion().toString();
		final String location = tb23.getLocation();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String NAME = PID_ROOT + ".TargetedPIDRequiredNoModified";
		final String PID1 = NAME;
		final String PID2 = String.format("%s|%s", PID1, bsn);
		final String PID3 = String.format("%s|%s|%s", PID1, bsn, version);
		final String PID4 = String.format("%s|%s|%s|%s", PID1, bsn, version, location);

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ BaseService.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				Dictionary<String, Object> props;
				base1Tracker.open();
				BaseService b1;
				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("base1 available").isNull();

				Configuration config1 = cm.getConfiguration(PID1, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config1");
				props.put(PID1, "config1");
				config1.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				Configuration config2 = cm.getConfiguration(PID2, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config2");
				props.put(PID2, "config2");
				config2.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				Configuration config3 = cm.getConfiguration(PID3, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config3");
				props.put(PID3, "config3");
				config3.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				Configuration config4 = cm.getConfiguration(PID4, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config4");
				props.put(PID4, "config4");
				config4.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config4");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID4);

				config4.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				config3.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				config2.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				config1.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("base1 available").isNull();
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationTargetedPIDRequiredModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		final String bsn = tb23.getSymbolicName();
		final String version = tb23.getVersion().toString();
		final String location = tb23.getLocation();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String NAME = PID_ROOT + ".TargetedPIDRequiredModified";
		final String PID1 = NAME;
		final String PID2 = String.format("%s|%s", PID1, bsn);
		final String PID3 = String.format("%s|%s|%s", PID1, bsn, version);
		final String PID4 = String.format("%s|%s|%s|%s", PID1, bsn, version, location);

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ BaseService.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				Dictionary<String, Object> props;
				base1Tracker.open();
				BaseService b1;
				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("base1 available").isNull();

				Configuration config1 = cm.getConfiguration(PID1, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config1");
				props.put(PID1, "config1");
				config1.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				Configuration config2 = cm.getConfiguration(PID2, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config2");
				props.put(PID2, "config2");
				config2.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				Configuration config3 = cm.getConfiguration(PID3, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config3");
				props.put(PID3, "config3");
				config3.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				Configuration config4 = cm.getConfiguration(PID4, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config4");
				props.put(PID4, "config4");
				config4.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config4");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID4);

				config4.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				config3.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				config2.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				config1.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("base1 available").isNull();
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationTargetedPIDOptionalNoModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		final String bsn = tb23.getSymbolicName();
		final String version = tb23.getVersion().toString();
		final String location = tb23.getLocation();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String NAME = PID_ROOT + ".TargetedPIDOptionalNoModified";
		final String PID1 = NAME;
		final String PID2 = String.format("%s|%s", PID1, bsn);
		final String PID3 = String.format("%s|%s|%s", PID1, bsn, version);
		final String PID4 = String.format("%s|%s|%s|%s", PID1, bsn, version, location);

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ BaseService.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				Dictionary<String, Object> props;
				base1Tracker.open();
				BaseService b1;
				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("xml");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isNull();

				Configuration config1 = cm.getConfiguration(PID1, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config1");
				props.put(PID1, "config1");
				config1.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				Configuration config2 = cm.getConfiguration(PID2, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config2");
				props.put(PID2, "config2");
				config2.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				Configuration config3 = cm.getConfiguration(PID3, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config3");
				props.put(PID3, "config3");
				config3.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				Configuration config4 = cm.getConfiguration(PID4, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config4");
				props.put(PID4, "config4");
				config4.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config4");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID4);

				config4.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				config3.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				config2.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				config1.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("xml");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isNull();
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testConfigurationTargetedPIDOptionalModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		Bundle tb23 = installer.installBundle(context.getBundle().getEntry("tb23.jar"), false);
		assertThat(tb23).as("tb23 failed to install").isNotNull();

		final String bsn = tb23.getSymbolicName();
		final String version = tb23.getVersion().toString();
		final String location = tb23.getLocation();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb23";
		final String NAME = PID_ROOT + ".TargetedPIDOptionalModified";
		final String PID1 = NAME;
		final String PID2 = String.format("%s|%s", PID1, bsn);
		final String PID3 = String.format("%s|%s|%s", PID1, bsn, version);
		final String PID4 = String.format("%s|%s|%s|%s", PID1, bsn, version, location);

		try {
			tb23.start();

			Filter base1Filter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ BaseService.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> base1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, base1Filter, null);
			try {
				Dictionary<String, Object> props;
				base1Tracker.open();
				BaseService b1;
				b1 = base1Tracker.waitForService(SLEEP * 3);
				assertThat(b1).as("missing base1").isNotNull();
				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("xml");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isNull();

				Configuration config1 = cm.getConfiguration(PID1, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config1");
				props.put(PID1, "config1");
				config1.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				Configuration config2 = cm.getConfiguration(PID2, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config2");
				props.put(PID2, "config2");
				config2.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				Configuration config3 = cm.getConfiguration(PID3, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config3");
				props.put(PID3, "config3");
				config3.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				Configuration config4 = cm.getConfiguration(PID4, null);
				props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config4");
				props.put(PID4, "config4");
				config4.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config4");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID4);

				config4.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config3");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID3);

				config3.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config2");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID2);

				config2.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("config1");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isEqualTo(PID1);

				config1.delete();
				settle(SLEEP * 3); // wait for SCR to react to change

				props = b1.getProperties();
				assertThat(props).as("props null").isNotNull();
				assertThat(props.get(PID_ROOT)).as("wrong configuration precedence").isEqualTo("xml");
				assertThat(props.get(Constants.SERVICE_PID)).as("wrong service.pid").isNull();
			}
			finally {
				base1Tracker.close();
			}
		}
		finally {
			tb23.uninstall();
		}
	}

	@Test
	public void testStaticScalarFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.StaticScalarFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getService()).as("no service injected").isNotNull();
				assertThat(t1.getService()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getAssignable()).as("no service injected").isNotNull();
				assertThat(t1.getAssignable()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference()).as("wrong reference injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects()).as("no serviceobjects injected").isNotNull();
				assertThat(t1.getServiceObjects().getServiceReference()).as("wrong serviceobjects injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects().getService()).as("serviceobjects produced wrong service").isSameAs(c1);
				Map<String, Object> properties = t1.getProperties();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				Map<String, Object> key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("tuple key wrong").isEqualTo(properties.get(ComponentConstants.COMPONENT_ID));
				assertThat(t1.getTuple() instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isEqualTo(0);
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isEqualTo(0);
				assertThat(t1.getTuple().getValue()).as("tuple value null").isNotNull();
				assertThat(t1.getTuple().getValue()).as("tuple value wrong").isSameAs(t1.getService());
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@SuppressWarnings("unchecked")
	private static <C> Comparable<C> asComparable(C o) {
		return (Comparable<C>) o;
	}

	@Test
	public void testStaticScalarFieldReferenceModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb24";
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.StaticScalarFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("xml");
				ScalarFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				assertThat(t1.getProperties()).as("no properties injected").isNotNull();
				assertThat(t1.getProperties().get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				Map<String, Object> key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				Configuration config = cm.getConfiguration("org.osgi.test.cases.component.tb24.Ranking1", null);
				Dictionary<String, Object> props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config");
				config.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("config");
				ScalarFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("test service not reactivated").isNotSameAs(t1);
				assertThat(t2.getActivationCount()).as("wrong activation count").isEqualTo(2);
				assertThat(t2.getModificationCount()).as("wrong modification count").isEqualTo(0);
				assertThat(t2.getDeactivationCount()).as("wrong deactivation count").isEqualTo(1);
				assertThat(t2.getReference()).as("no reference injected").isNotNull();
				assertThat(t2.getReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				assertThat(t2.getProperties()).as("no properties injected").isNotNull();
				assertThat(t2.getProperties().get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				assertThat(t2.getTuple()).as("no tuple injected").isNotNull();
				key = t2.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testDynamicScalarFieldReferenceModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();

		final String PID_ROOT = TEST_CASE_ROOT + ".tb24";
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicScalarFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("xml");
				ScalarFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				assertThat(t1.getProperties()).as("no properties injected").isNotNull();
				assertThat(t1.getProperties().get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				Map<String, Object> key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				Configuration config = cm.getConfiguration("org.osgi.test.cases.component.tb24.Ranking1", null);
				Dictionary<String, Object> props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config");
				config.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("config");
				assertThat(test1Tracker.waitForService(SLEEP * 3)).as("test service reactivated").isSameAs(t1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isEqualTo(1);
				assertThat(t1.getModificationCount()).as("wrong modification count").isEqualTo(0);
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isEqualTo(0);
				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				assertThat(t1.getProperties()).as("no properties injected").isNotNull();
				assertThat(t1.getProperties().get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testDynamicNonVoltaileFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicNonVolatileFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> s1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(s1).as("missing test1").isNotNull();
				assertThat(s1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(s1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(s1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(s1.getService()).as("service injected").isNull();
				assertThat(s1.getAssignable()).as("service injected").isNull();
				assertThat(s1.getReference()).as("reference injected").isNull();
				assertThat(s1.getServiceObjects()).as("serviceobjects injected").isNull();
				assertThat(s1.getProperties()).as("properties injected").isNull();
				assertThat(s1.getTuple()).as("tuple injected").isNull();

				@SuppressWarnings("unchecked")
				MultipleFieldTestService<BaseService> t1 = (MultipleFieldTestService<BaseService>) s1;
				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs1 instanceof TestList).as("service collection replaced").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr1 instanceof TestList).as("service reference collection replaced").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co1 instanceof TestList).as("service objects collection replaced").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp1 instanceof TestList).as("service properties collection replaced").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct1 instanceof TestList).as("service tuple collection replaced").isTrue();

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls1 instanceof TestList).as("service list replaced").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr1 instanceof TestList).as("service reference list replaced").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo1 instanceof TestList).as("service objects list replaced").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp1 instanceof TestList).as("service properties list replaced").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt1 instanceof TestList).as("service tuple list replaced").isTrue();

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testFinalFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.FinalFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> s1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(s1).as("missing test1").isNotNull();
				assertThat(s1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(s1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(s1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(s1.getService()).as("service injected").isNull();
				assertThat(s1.getAssignable()).as("service injected").isNull();
				assertThat(s1.getReference()).as("reference injected").isNull();
				assertThat(s1.getServiceObjects()).as("serviceobjects injected").isNull();
				assertThat(s1.getProperties()).as("properties injected").isNull();
				assertThat(s1.getTuple()).as("tuple injected").isNull();

				@SuppressWarnings("unchecked")
				MultipleFieldTestService<BaseService> t1 = (MultipleFieldTestService<BaseService>) s1;
				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs1 instanceof TestList).as("service collection replaced").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr1 instanceof TestList).as("service reference collection replaced").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co1 instanceof TestList).as("service objects collection replaced").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp1 instanceof TestList).as("service properties collection replaced").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct1 instanceof TestList).as("service tuple collection replaced").isTrue();

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls1 instanceof TestList).as("service list replaced").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr1 instanceof TestList).as("service reference list replaced").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo1 instanceof TestList).as("service objects list replaced").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp1 instanceof TestList).as("service properties list replaced").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt1 instanceof TestList).as("service tuple list replaced").isTrue();
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testOptionalScalarFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.OptionalScalarFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getService()).as("no service injected").isNotNull();
				assertThat(t1.getService()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getAssignable()).as("no service injected").isNotNull();
				assertThat(t1.getAssignable()).as("wrong service injected").isSameAs(c1);

				assertThat(t1.getReference()).as("field not nulled").isNull();
				assertThat(t1.getServiceObjects()).as("field not nulled").isNull();
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testDynamicScalarFieldReference130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicScalarFieldReceiver";

		try {
			tb24.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb24,
					"org.osgi.test.cases.component.tb24.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking*))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getService()).as("no service injected").isNotNull();
				assertThat(t1.getService()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getAssignable()).as("no service injected").isNotNull();
				assertThat(t1.getAssignable()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference()).as("wrong reference injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects()).as("no serviceobjects injected").isNotNull();
				assertThat(t1.getServiceObjects().getServiceReference()).as("wrong serviceobjects injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects().getService()).as("serviceobjects produced wrong service").isSameAs(c1);
				Map<String, Object> properties = t1.getProperties();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				Map<String, Object> key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("tuple key wrong").isEqualTo(properties.get(ComponentConstants.COMPONENT_ID));
				assertThat(t1.getTuple() instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(t1.getTuple().getValue()).as("tuple value null").isNotNull();
				assertThat(t1.getTuple().getValue()).as("tuple value wrong").isSameAs(t1.getService());

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();
				BaseService c10 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c10).as("missing comp10").isNotNull();
				assertThat(c10).as("did not get higher ranked service").isNotSameAs(c1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(t1.getService()).as("no service injected").isNotNull();
				assertThat(t1.getService()).as("wrong service injected").isSameAs(c10);
				assertThat(t1.getAssignable()).as("no service injected").isNotNull();
				assertThat(t1.getAssignable()).as("wrong service injected").isSameAs(c10);
				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference()).as("wrong reference injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects()).as("no serviceobjects injected").isNotNull();
				assertThat(t1.getServiceObjects().getServiceReference()).as("wrong serviceobjects injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects().getService()).as("serviceobjects produced wrong service").isSameAs(c10);
				properties = t1.getProperties();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c10.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("tuple key wrong").isEqualTo(properties.get(ComponentConstants.COMPONENT_ID));
				assertThat(t1.getTuple() instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(t1.getTuple().getValue()).as("tuple value null").isNotNull();
				assertThat(t1.getTuple().getValue()).as("tuple value wrong").isSameAs(t1.getService());

				p = scr.disableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isFalse();
				assertThat(comp1Tracker.waitForService(SLEEP * 3)).as("not back to comp1").isSameAs(c1);

				assertThat(t1.getService()).as("no service injected").isNotNull();
				assertThat(t1.getService()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getAssignable()).as("no service injected").isNotNull();
				assertThat(t1.getAssignable()).as("wrong service injected").isSameAs(c1);
				assertThat(t1.getReference()).as("no reference injected").isNotNull();
				assertThat(t1.getReference()).as("wrong reference injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects()).as("no serviceobjects injected").isNotNull();
				assertThat(t1.getServiceObjects().getServiceReference()).as("wrong serviceobjects injected").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(t1.getServiceObjects().getService()).as("serviceobjects produced wrong service").isSameAs(c1);
				properties = t1.getProperties();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(t1.getTuple()).as("no tuple injected").isNotNull();
				key = t1.getTuple().getKey();
				assertThat(key).as("tuple key null").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("tuple key wrong").isEqualTo(properties.get(ComponentConstants.COMPONENT_ID));
				assertThat(t1.getTuple() instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(t1.getTuple().getValue()).as("tuple value null").isNotNull();
				assertThat(t1.getTuple().getValue()).as("tuple value wrong").isSameAs(t1.getService());

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testStaticMultipleFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.StaticMultipleFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName()
							+ ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(1);
				assertThat(cs1.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				assertThat(cs1.remove(cs1.iterator().next())).as("service collection not mutable").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1 instanceof TestList).as("service reference collection not replaced").isFalse();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(1);
				assertThat(cr1.iterator().next()).as("wrong service in service reference collection").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(cr1.remove(cr1.iterator().next())).as("service reference collection not mutable").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1 instanceof TestList).as("service objects collection not replaced").isFalse();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(1);
				assertThat(co1.iterator().next().getServiceReference()).as("wrong service in service objects collection").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(co1.remove(co1.iterator().next())).as("service objects collection not mutable").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1 instanceof TestList).as("service properties collection not replaced").isFalse();
				assertThat(cp1.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				Map<String, Object> properties = cp1.iterator().next();
				assertThat(cp1.remove(properties)).as("service properties collection not mutable").isTrue();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1 instanceof TestList).as("service tuple collection not replaced").isFalse();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(1);
				Entry<Map<String, Object>, BaseService> tuple = ct1.iterator().next();
				assertThat(ct1.remove(tuple)).as("service tuple collection not mutable").isTrue();
				Map<String, Object> key = tuple.getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("wrong tuple injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(tuple instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(tuple.getValue()).as("tuple value null").isNotNull();
				assertThat(tuple.getValue()).as("tuple value wrong").isSameAs(c1);

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(1);
				assertThat(ls1.get(0)).as("wrong service in service list").isSameAs(c1);
				assertThat(ls1.remove(ls1.get(0))).as("service list not mutable").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1 instanceof TestList).as("service reference list not replaced").isFalse();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(1);
				assertThat(lr1.get(0)).as("wrong service in service reference list").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(lr1.remove(lr1.get(0))).as("service reference list not mutable").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1 instanceof TestList).as("service objects list not replaced").isFalse();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(1);
				assertThat(lo1.get(0).getServiceReference()).as("wrong service in service objects list").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(lo1.get(0).getService()).as("serviceobjects produced wrong service").isSameAs(c1);
				assertThat(lo1.remove(lo1.get(0))).as("service objects list not mutable").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1 instanceof TestList).as("service properties list not replaced").isFalse();
				assertThat(lp1.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp1.get(0);
				assertThat(lp1.remove(properties)).as("service properties list not mutable").isTrue();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1 instanceof TestList).as("service tuple list not replaced").isFalse();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(1);
				assertThat(lt1.get(0) instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(asComparable(lt1.get(0)).compareTo(tuple)).as("tuples not equal").isZero();
				assertThat(asComparable(tuple).compareTo(lt1.get(0))).as("tuples not equal").isZero();
				tuple = lt1.get(0);
				assertThat(lt1.remove(tuple)).as("service tuple list not mutable").isTrue();
				key = tuple.getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("wrong tuple injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(tuple.getValue()).as("tuple value null").isNotNull();
				assertThat(tuple.getValue()).as("tuple value wrong").isSameAs(c1);
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testStaticMultipleFieldReferenceModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		final String PID_ROOT = TEST_CASE_ROOT + ".tb24";

		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.StaticMultipleFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isEqualTo(1);
				assertThat(t1.getModificationCount()).as("wrong modification count").isEqualTo(0);
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isEqualTo(0);

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1.size()).as("wrong number of elements in service collection").isEqualTo(1);
				assertThat(cs1.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1.size()).as("wrong number of elements in service reference collection").isEqualTo(1);
				assertThat(cr1.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1.size()).as("wrong number of elements in service objects collection").isEqualTo(1);
				assertThat(co1.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				Map<String, Object> properties = cp1.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1.size()).as("wrong number of elements in service tuple collection").isEqualTo(1);
				Map<String, Object> key = ct1.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1.size()).as("wrong number of elements in service list").isEqualTo(1);
				assertThat(ls1.get(0)).as("wrong service in service list").isSameAs(c1);
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1.size()).as("wrong number of elements in service reference list").isEqualTo(1);
				assertThat(lr1.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1.size()).as("wrong number of elements in service objects list").isEqualTo(1);
				assertThat(lo1.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp1.get(0);
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1.size()).as("wrong number of elements in service tuple list").isEqualTo(1);
				key = lt1.get(0).getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				Configuration config = cm.getConfiguration("org.osgi.test.cases.component.tb24.Ranking1", null);
				Dictionary<String, Object> props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config");
				config.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("config");
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("test service not reactivated").isNotSameAs(t1);
				assertThat(t2.getActivationCount()).as("wrong activation count").isEqualTo(2);
				assertThat(t2.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t2.getDeactivationCount()).as("wrong deactivation count").isOne();

				Collection<BaseService> cs2 = t2.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(1);
				assertThat(cs2.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				Collection<ServiceReference<BaseService>> cr2 = t2.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(1);
				assertThat(cr2.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<ComponentServiceObjects<BaseService>> co2 = t2.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(1);
				assertThat(co2.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Map<String, Object>> cp2 = t2.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection not replaced").isNotSameAs(cp1);
				assertThat(cp2.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				properties = cp2.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t2.getCollectionTuple();
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("service tuple collection not replaced").isNotSameAs(ct1);
				assertThat(ct2.size()).as("wrong number of elements in service tuple collection").isEqualTo(1);
				key = ct2.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");

				List<BaseService> ls2 = t2.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2.size()).as("wrong number of elements in service list").isEqualTo(1);
				assertThat(ls2.get(0)).as("wrong service in service list").isSameAs(c1);
				List<ServiceReference<BaseService>> lr2 = t2.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2.size()).as("wrong number of elements in service reference list").isEqualTo(1);
				assertThat(lr2.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<ComponentServiceObjects<BaseService>> lo2 = t2.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2.size()).as("wrong number of elements in service objects list").isEqualTo(1);
				assertThat(lo2.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<Map<String, Object>> lp2 = t2.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp2.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp2.get(0);
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<Entry<Map<String, Object>, BaseService>> lt2 = t2.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt2.size()).as("wrong number of elements in service tuple list").isEqualTo(1);
				key = lt2.get(0).getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testUpdateStaticMultipleFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.UpdateStaticMultipleFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs1 instanceof TestList).as("service collection replaced").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr1 instanceof TestList).as("service reference collection replaced").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co1 instanceof TestList).as("service objects collection replaced").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp1 instanceof TestList).as("service properties collection replaced").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct1 instanceof TestList).as("service tuple collection replaced").isTrue();

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls1 instanceof TestList).as("service list replaced").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr1 instanceof TestList).as("service reference list replaced").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo1 instanceof TestList).as("service objects list replaced").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp1 instanceof TestList).as("service properties list replaced").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt1 instanceof TestList).as("service tuple list replaced").isTrue();

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOptionalStaticMultipleFieldReference130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.OptionalStaticMultipleFieldReceiver";

		try {
			tb24.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb24,
					"org.osgi.test.cases.component.tb24.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking10))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("found comp1").isNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs1 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs1.add(mock(BaseService.class))).as("service collection not mutable").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr1 instanceof TestList).as("service reference collection not replaced").isFalse();
				assertThat(cr1.add(comp1Tracker.getServiceReference())).as("service reference collection not mutable").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co1 instanceof TestList).as("service objects collection not replaced").isFalse();
				assertThat(co1.add(mock(ComponentServiceObjects.class))).as("service objects collection not mutable").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp1 instanceof TestList).as("service properties collection not replaced").isFalse();
				assertThat(cp1.add(mock(Map.class))).as("service properties collection not mutable").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct1 instanceof TestList).as("service tuple collection not replaced").isFalse();
				assertThat(ct1.add(mock(Entry.class))).as("service tuple collection not mutable").isTrue();

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls1 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls1.add(mock(BaseService.class))).as("service list not mutable").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr1 instanceof TestList).as("service reference list not replaced").isFalse();
				assertThat(lr1.add(comp1Tracker.getServiceReference())).as("service reference list not mutable").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo1 instanceof TestList).as("service objects list not replaced").isFalse();
				assertThat(lo1.add(mock(ComponentServiceObjects.class))).as("service objects list not mutable").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp1 instanceof TestList).as("service properties list not replaced").isFalse();
				assertThat(lp1.add(mock(Map.class))).as("service properties list not mutable").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt1 instanceof TestList).as("service tuple list not replaced").isFalse();
				assertThat(lt1.add(mock(Entry.class))).as("service tuple list not mutable").isTrue();

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("missing test1").isNotNull();
				assertThat(t1).as("not reactivated").isNotSameAs(t2);
				assertThat(t2.getActivationCount()).as("wrong activation count").isEqualTo(2);
				assertThat(t2.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t2.getDeactivationCount()).as("wrong deactivation count").isOne();

				Collection<BaseService> cs2 = t2.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("service collection not replaced").isNotSameAs(cs1);
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(1);
				assertThat(cs2 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs2.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				Collection<ServiceReference<BaseService>> cr2 = t2.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("service reference collection not replaced").isNotSameAs(cr1);
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(1);
				assertThat(cr2 instanceof TestList).as("service reference collection not replaced").isFalse();
				Collection<ComponentServiceObjects<BaseService>> co2 = t2.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("service objects collection not replaced").isNotSameAs(co1);
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(1);
				assertThat(co2 instanceof TestList).as("service objects collection not replaced").isFalse();
				Collection<Map<String, Object>> cp2 = t2.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection not replaced").isNotSameAs(cp1);
				assertThat(cp2).as("wrong number of elements in service properties collection").hasSize(1);
				assertThat(cp2 instanceof TestList).as("service properties collection not replaced").isFalse();
				Map<String, Object> properties = cp2.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t2.getCollectionTuple();
				assertThat(ct2).as("service tuple collection not replaced").isNotSameAs(ct1);
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("wrong number of elements in service tuple collection").hasSize(1);
				assertThat(ct2 instanceof TestList).as("service tuple collection not replaced").isFalse();
				Map<String, Object> key = ct2.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();

				List<BaseService> ls2 = t2.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2).as("service list not replaced").isNotSameAs(ls1);
				assertThat(ls2).as("wrong number of elements in service list").hasSize(1);
				assertThat(ls2 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls2.get(0)).as("wrong service in service list").isSameAs(c1);
				List<ServiceReference<BaseService>> lr2 = t2.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2).as("service reference list not replaced").isNotSameAs(lr1);
				assertThat(lr2).as("wrong number of elements in service reference list").hasSize(1);
				assertThat(lr2 instanceof TestList).as("service reference list not replaced").isFalse();
				List<ComponentServiceObjects<BaseService>> lo2 = t2.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2).as("service objects list not replaced").isNotSameAs(lo1);
				assertThat(lo2).as("wrong number of elements in service objects list").hasSize(1);
				assertThat(lo2 instanceof TestList).as("service objects list not replaced").isFalse();
				List<Map<String, Object>> lp2 = t2.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp2).as("wrong number of elements in service properties list").hasSize(1);
				assertThat(lp2 instanceof TestList).as("service properties list not replaced").isFalse();
				properties = lp2.get(0);
				assertThat(properties).as("no properties injected").isNotNull();
				List<Entry<Map<String, Object>, BaseService>> lt2 = t2.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt2).as("wrong number of elements in service tuple list").hasSize(1);
				assertThat(lt2 instanceof TestList).as("service tuple list not replaced").isFalse();
				key = lt2.get(0).getKey();
				assertThat(key).as("no tuple injected").isNotNull();

				p = scr.disableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isFalse();

				c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("found comp1").isNull();
				MultipleFieldTestService<BaseService> t3 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t3).as("missing test3").isNotNull();
				assertThat(t1).as("not reactivated").isNotSameAs(t3);
				assertThat(t2).as("not reactivated").isNotSameAs(t3);
				assertThat(t3.getActivationCount()).as("wrong activation count").isEqualTo(3);
				assertThat(t3.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t3.getDeactivationCount()).as("wrong deactivation count").isEqualTo(2);

				Collection<BaseService> cs3 = t3.getCollectionService();
				assertThat(cs3).as("no service collection").isNotNull();
				assertThat(cs3).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs3 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs3).as("service collection not replaced").isNotSameAs(cs1);
				assertThat(cs3).as("service collection not replaced").isNotSameAs(cs2);
				Collection<ServiceReference<BaseService>> cr3 = t3.getCollectionReference();
				assertThat(cr3).as("no service reference collection").isNotNull();
				assertThat(cr3).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr3 instanceof TestList).as("service reference collection not replaced").isFalse();
				assertThat(cr3).as("service reference collection not replaced").isNotSameAs(cr1);
				assertThat(cr3).as("service reference collection not replaced").isNotSameAs(cr2);
				Collection<ComponentServiceObjects<BaseService>> co3 = t3.getCollectionServiceObjects();
				assertThat(co3).as("no service objects collection").isNotNull();
				assertThat(co3).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co3 instanceof TestList).as("service objects collection not replaced").isFalse();
				assertThat(co3).as("service objects collection not replaced").isNotSameAs(co1);
				assertThat(co3).as("service objects collection not replaced").isNotSameAs(co2);
				Collection<Map<String, Object>> cp3 = t3.getCollectionProperties();
				assertThat(cp3).as("no service properties collection").isNotNull();
				assertThat(cp3).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp3 instanceof TestList).as("service properties collection not replaced").isFalse();
				assertThat(cp3).as("service properties collection not replaced").isNotSameAs(cp1);
				assertThat(cp3).as("service properties collection not replaced").isNotSameAs(cp2);
				Collection<Entry<Map<String, Object>, BaseService>> ct3 = t3.getCollectionTuple();
				assertThat(ct3).as("no service tuple collection").isNotNull();
				assertThat(ct3).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct3 instanceof TestList).as("service tuple collection not replaced").isFalse();
				assertThat(ct3).as("service tuple collection not replaced").isNotSameAs(ct1);
				assertThat(ct3).as("service tuple collection not replaced").isNotSameAs(ct2);

				List<BaseService> ls3 = t3.getListService();
				assertThat(ls3).as("no service list").isNotNull();
				assertThat(ls3).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls3 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls3).as("service list not replaced").isNotSameAs(ls1);
				assertThat(ls3).as("service list not replaced").isNotSameAs(ls2);
				List<ServiceReference<BaseService>> lr3 = t3.getListReference();
				assertThat(lr3).as("no service reference list").isNotNull();
				assertThat(lr3).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr3 instanceof TestList).as("service reference list not replaced").isFalse();
				assertThat(lr3).as("service reference list not replaced").isNotSameAs(lr1);
				assertThat(lr3).as("service reference list not replaced").isNotSameAs(lr2);
				List<ComponentServiceObjects<BaseService>> lo3 = t3.getListServiceObjects();
				assertThat(lo3).as("no service objects list").isNotNull();
				assertThat(lo3).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo3 instanceof TestList).as("service objects list not replaced").isFalse();
				assertThat(lo3).as("service objects list not replaced").isNotSameAs(lo1);
				assertThat(lo3).as("service objects list not replaced").isNotSameAs(lo2);
				List<Map<String, Object>> lp3 = t3.getListProperties();
				assertThat(lp3).as("no service properties list").isNotNull();
				assertThat(lp3).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp3 instanceof TestList).as("service properties list not replaced").isFalse();
				assertThat(lp3).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp3).as("service properties list not replaced").isNotSameAs(lp2);
				List<Entry<Map<String, Object>, BaseService>> lt3 = t3.getListTuple();
				assertThat(lt3).as("no service tuple list").isNotNull();
				assertThat(lt3).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt3 instanceof TestList).as("service tuple list not replaced").isFalse();
				assertThat(lt3).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt3).as("service tuple list not replaced").isNotSameAs(lt2);

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testSortingStaticMultipleFieldReference130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.StaticMultipleFieldReceiver";

		try {
			tb24.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb24,
					"org.osgi.test.cases.component.tb24.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter comp10Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking10))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<BaseService, BaseService> comp10Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp10Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				comp10Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				BaseService c10 = comp10Tracker.waitForService(SLEEP * 3);
				assertThat(c10).as("found comp10").isNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(1);
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(1);
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(1);
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(1);
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(1);

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(1);
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(1);
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(1);
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(1);
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(1);

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				c10 = comp10Tracker.waitForService(SLEEP * 3);
				assertThat(c10).as("missing comp10").isNotNull();
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("missing test1").isNotNull();
				assertThat(t1).as("not reactivated").isNotSameAs(t2);
				assertThat(t2.getActivationCount()).as("wrong activation count").isEqualTo(2);
				assertThat(t2.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t2.getDeactivationCount()).as("wrong deactivation count").isOne();

				Collection<BaseService> cs2 = t2.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("service collection not replaced").isNotSameAs(cs1);
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(2);
				assertThat(cs2).as("wrong order in service collection").isEqualTo(Arrays.asList(c1, c10));
				Collection<ServiceReference<BaseService>> cr2 = t2.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("service reference collection not replaced").isNotSameAs(cr1);
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(2);
				assertThat(cr2).as("wrong order in service reference collection").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				Collection<ComponentServiceObjects<BaseService>> co2 = t2.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("service objects collection not replaced").isNotSameAs(co1);
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(2);
				Iterator<ComponentServiceObjects<BaseService>> co2i = co2.iterator();
				assertThat(Arrays.asList(co2i.next().getServiceReference(), co2i.next().getServiceReference())).as("wrong order in service objects collection").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				Collection<Map<String, Object>> cp2 = t2.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection not replaced").isNotSameAs(cp1);
				assertThat(cp2).as("wrong number of elements in service properties collection").hasSize(2);
				Iterator<Map<String, Object>> cp2i = cp2.iterator();
				assertThat(asComparable(cp2i.next()).compareTo(cp2i.next()) < 0).as("wrong order in service properties collection").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t2.getCollectionTuple();
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("service tuple collection not replaced").isNotSameAs(ct1);
				assertThat(ct2).as("wrong number of elements in service tuple collection").hasSize(2);
				Iterator<Entry<Map<String, Object>, BaseService>> ct2i = ct2.iterator();
				assertThat(asComparable(ct2i.next()).compareTo(ct2i.next()) < 0).as("wrong order in service tuple collection").isTrue();

				List<BaseService> ls2 = t2.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2).as("service list not replaced").isNotSameAs(ls1);
				assertThat(ls2).as("wrong number of elements in service list").hasSize(2);
				assertThat(ls2).as("wrong order in service list").isEqualTo(Arrays.asList(c1, c10));
				List<ServiceReference<BaseService>> lr2 = t2.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2).as("service reference list not replaced").isNotSameAs(lr1);
				assertThat(lr2).as("wrong number of elements in service reference list").hasSize(2);
				assertThat(lr2).as("wrong order in service reference list").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				List<ComponentServiceObjects<BaseService>> lo2 = t2.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2).as("service objects list not replaced").isNotSameAs(lo1);
				assertThat(lo2).as("wrong number of elements in service objects list").hasSize(2);
				assertThat(Arrays.asList(lo2.get(0).getServiceReference(), lo2.get(1).getServiceReference())).as("wrong order in service objects list").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				List<Map<String, Object>> lp2 = t2.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp2).as("wrong number of elements in service properties list").hasSize(2);
				assertThat(asComparable(lp2.get(0)).compareTo(lp2.get(1)) < 0).as("wrong order in service properties list").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt2 = t2.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt2).as("wrong number of elements in service tuple list").hasSize(2);
				assertThat(asComparable(lt2.get(0)).compareTo(lt2.get(1)) < 0).as("wrong order in service tuple list").isTrue();
			}
			finally {
				comp1Tracker.close();
				comp10Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testNonInstanceFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.NonInstanceFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> s1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(s1).as("missing test1").isNotNull();
				assertThat(s1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(s1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(s1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(s1.getService()).as("service injected").isNull();
				assertThat(s1.getAssignable()).as("service injected").isNull();
				assertThat(s1.getReference()).as("reference injected").isNull();
				assertThat(s1.getServiceObjects()).as("serviceobjects injected").isNull();
				assertThat(s1.getProperties()).as("properties injected").isNull();
				assertThat(s1.getTuple()).as("tuple injected").isNull();

				@SuppressWarnings("unchecked")
				MultipleFieldTestService<BaseService> t1 = (MultipleFieldTestService<BaseService>) s1;
				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				assertThat(cs1 instanceof TestList).as("service collection replaced").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				assertThat(cr1 instanceof TestList).as("service reference collection replaced").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				assertThat(co1 instanceof TestList).as("service objects collection replaced").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				assertThat(cp1 instanceof TestList).as("service properties collection replaced").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);
				assertThat(ct1 instanceof TestList).as("service tuple collection replaced").isTrue();

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				assertThat(ls1 instanceof TestList).as("service list replaced").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				assertThat(lr1 instanceof TestList).as("service reference list replaced").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				assertThat(lo1 instanceof TestList).as("service objects list replaced").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				assertThat(lp1 instanceof TestList).as("service properties list replaced").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);
				assertThat(lt1 instanceof TestList).as("service tuple list replaced").isTrue();

				Collection<BaseService> ss1 = t1.getCollectionSubtypeService();
				assertThat(ss1).as("no service collection subtype").isNotNull();
				assertThat(ss1).as("wrong number of elements in service collection subtype").hasSize(0);
				assertThat(ss1 instanceof TestSet).as("service collection subtype replaced").isTrue();
				Collection<ServiceReference<BaseService>> sr1 = t1.getCollectionSubtypeReference();
				assertThat(sr1).as("no service reference collection subtype").isNotNull();
				assertThat(sr1).as("wrong number of elements in service reference collection subtype").hasSize(0);
				Collection<ComponentServiceObjects<BaseService>> so1 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so1).as("no service objects collection subtype").isNotNull();
				assertThat(so1).as("wrong number of elements in service objects collection subtype").hasSize(0);
				Collection<Map<String, Object>> sp1 = t1.getCollectionSubtypeProperties();
				assertThat(sp1).as("no service properties collection subtype").isNotNull();
				assertThat(sp1).as("wrong number of elements in service properties collection subtype").hasSize(0);
				assertThat(sp1 instanceof TestIdentitySet).as("service properties collection subtype replaced").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> st1 = t1.getCollectionSubtypeTuple();
				assertThat(st1).as("no service tuple collection subtype").isNotNull();
				assertThat(st1).as("wrong number of elements in service tuple collection subtype").hasSize(0);
				assertThat(st1 instanceof TestIdentitySet).as("service tuple collection subtype replaced").isTrue();

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testBadFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.BadFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + ScalarFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>> test1Tracker = new ServiceTracker<ScalarFieldTestService<BaseService>, ScalarFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				ScalarFieldTestService<BaseService> s1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(s1).as("missing test1").isNotNull();
				assertThat(s1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(s1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(s1.getDeactivationCount()).as("wrong deactivation count").isZero();

				assertThat(s1.getAssignable()).as("service injected").isNull();

				@SuppressWarnings("unchecked")
				MultipleFieldTestService<BaseService> t1 = (MultipleFieldTestService<BaseService>) s1;
				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("service collection injected").isNull();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("service reference collection injected").isNull();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("service objects collection injected").isNull();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("service properties collection injected").isNull();
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("service tuple collection injected").isNull();
				Collection<BaseService> ss1 = t1.getCollectionSubtypeService();
				assertThat(ss1).as("service collection subtype should not be modified").isNull();
			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testDynamicMultipleFieldReference130() throws Exception {
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicMultipleFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(1);
				assertThat(cs1.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				assertThat(cs1.remove(cs1.iterator().next())).as("service collection not mutable").isTrue();
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1 instanceof TestList).as("service reference collection not replaced").isFalse();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(1);
				assertThat(cr1.iterator().next()).as("wrong service in service reference collection").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(cr1.remove(cr1.iterator().next())).as("service reference collection not mutable").isTrue();
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1 instanceof TestList).as("service objects collection not replaced").isFalse();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(1);
				assertThat(co1.iterator().next().getServiceReference()).as("wrong service in service objects collection").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(co1.remove(co1.iterator().next())).as("service objects collection not mutable").isTrue();
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1 instanceof TestList).as("service properties collection not replaced").isFalse();
				assertThat(cp1.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				Map<String, Object> properties = cp1.iterator().next();
				assertThat(cp1.remove(properties)).as("service properties collection not mutable").isTrue();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1 instanceof TestList).as("service tuple collection not replaced").isFalse();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(1);
				Entry<Map<String, Object>, BaseService> tuple = ct1.iterator().next();
				assertThat(ct1.remove(tuple)).as("service tuple collection not mutable").isTrue();
				Map<String, Object> key = tuple.getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("wrong tuple injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(tuple instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(tuple.getValue()).as("tuple value null").isNotNull();
				assertThat(tuple.getValue()).as("tuple value wrong").isSameAs(c1);

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(1);
				assertThat(ls1.get(0)).as("wrong service in service list").isSameAs(c1);
				assertThat(ls1.remove(ls1.get(0))).as("service list not mutable").isTrue();
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1 instanceof TestList).as("service reference list not replaced").isFalse();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(1);
				assertThat(lr1.get(0)).as("wrong service in service reference list").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(lr1.remove(lr1.get(0))).as("service reference list not mutable").isTrue();
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1 instanceof TestList).as("service objects list not replaced").isFalse();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(1);
				assertThat(lo1.get(0).getServiceReference()).as("wrong service in service objects list").isEqualTo(comp1Tracker.getServiceReference());
				assertThat(lo1.get(0).getService()).as("serviceobjects produced wrong service").isSameAs(c1);
				assertThat(lo1.remove(lo1.get(0))).as("service objects list not mutable").isTrue();
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1 instanceof TestList).as("service properties list not replaced").isFalse();
				assertThat(lp1.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp1.get(0);
				assertThat(lp1.remove(properties)).as("service properties list not mutable").isTrue();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1 instanceof TestList).as("service tuple list not replaced").isFalse();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(1);
				assertThat(lt1.get(0) instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(asComparable(lt1.get(0)).compareTo(tuple)).as("tuples not equal").isZero();
				assertThat(asComparable(tuple).compareTo(lt1.get(0))).as("tuples not equal").isZero();
				tuple = lt1.get(0);
				assertThat(lt1.remove(tuple)).as("service tuple list not mutable").isTrue();
				key = tuple.getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("wrong tuple injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(tuple.getValue()).as("tuple value null").isNotNull();
				assertThat(tuple.getValue()).as("tuple value wrong").isSameAs(c1);

				Collection<BaseService> ss1 = t1.getCollectionSubtypeService();
				assertThat(ss1).as("no service collection subtype").isNotNull();
				assertThat(ss1 instanceof TestSet).as("service collection subtype replaced").isTrue();
				assertThat(ss1).as("wrong number of elements in service collection subtype").hasSize(1);
				assertThat(ss1.iterator().next()).as("wrong service in service collection subtype").isSameAs(c1);
				Collection<ServiceReference<BaseService>> sr1 = t1.getCollectionSubtypeReference();
				assertThat(sr1).as("no service reference collection subtype").isNotNull();
				assertThat(sr1).as("wrong number of elements in service reference collection subtype").hasSize(1);
				assertThat(sr1.iterator().next()).as("wrong service in service reference collection subtype").isEqualTo(comp1Tracker.getServiceReference());
				Collection<ComponentServiceObjects<BaseService>> so1 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so1).as("no service objects collection subtype").isNotNull();
				assertThat(so1).as("wrong number of elements in service objects collection subtype").hasSize(1);
				assertThat(so1.iterator().next().getServiceReference()).as("wrong service in service objects collection subtype").isEqualTo(comp1Tracker.getServiceReference());
				Collection<Map<String, Object>> sp1 = t1.getCollectionSubtypeProperties();
				assertThat(sp1).as("no service properties collection subtype").isNotNull();
				assertThat(sp1 instanceof TestIdentitySet).as("service properties collection subtype not replaced").isTrue();
				assertThat(sp1.size()).as("wrong number of elements in service properties collection subtype").isEqualTo(1);
				properties = sp1.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(ComponentConstants.COMPONENT_ID)).as("wrong properties injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(properties instanceof Comparable).as("properties not Comparable").isTrue();
				try {
					properties.remove(ComponentConstants.COMPONENT_ID);
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					properties.put("foo", "bar");
					fail("properties is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				Collection<Entry<Map<String, Object>, BaseService>> st1 = t1.getCollectionSubtypeTuple();
				assertThat(st1).as("no service tuple collection subtype").isNotNull();
				assertThat(st1 instanceof TestIdentitySet).as("service tuple collection subtype not replaced").isTrue();
				assertThat(st1.size()).as("wrong number of elements in service tuple collection subtype").isEqualTo(1);
				tuple = st1.iterator().next();
				key = tuple.getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(ComponentConstants.COMPONENT_ID)).as("wrong tuple injected").isEqualTo(c1.getProperties().get(ComponentConstants.COMPONENT_ID));
				assertThat(tuple instanceof Comparable).as("tuple not Comparable").isTrue();
				assertThat(key instanceof Comparable).as("tuple key not Comparable").isTrue();
				try {
					key.remove(ComponentConstants.COMPONENT_ID);
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				try {
					key.put("foo", "bar");
					fail("tuple key is not unmodifiable" + " expected:[" + UnsupportedOperationException.class.getName() + "] and got nothing");
				}
				catch (UnsupportedOperationException expected) {
					// map must be unmodifiable
				}
				assertThat(asComparable(properties).compareTo(key)).as("properties not equal to tuple key").isZero();
				assertThat(asComparable(key).compareTo(properties)).as("tuple keys not equal to properties").isZero();
				assertThat(tuple.getValue()).as("tuple value null").isNotNull();
				assertThat(tuple.getValue()).as("tuple value wrong").isSameAs(c1);

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testOptionalDynamicMultipleFieldReference130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.OptionalDynamicMultipleFieldReceiver";

		try {
			tb24.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb24,
					"org.osgi.test.cases.component.tb24.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking10))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("found comp1").isNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1 instanceof TestList).as("service collection not replaced").isFalse();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(0);
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1 instanceof TestList).as("service reference collection not replaced").isFalse();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(0);
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1 instanceof TestList).as("service objects collection not replaced").isFalse();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(0);
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1 instanceof TestList).as("service properties collection not replaced").isFalse();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(0);
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1 instanceof TestList).as("service tuple collection not replaced").isFalse();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(0);

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1 instanceof TestList).as("service list not replaced").isFalse();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(0);
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1 instanceof TestList).as("service reference list not replaced").isFalse();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(0);
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1 instanceof TestList).as("service objects list not replaced").isFalse();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(0);
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1 instanceof TestList).as("service properties list not replaced").isFalse();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(0);
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1 instanceof TestList).as("service tuple list not replaced").isFalse();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(0);

				Collection<BaseService> ss1 = t1.getCollectionSubtypeService();
				assertThat(ss1).as("no service collection subtype").isNotNull();
				assertThat(ss1 instanceof TestSet).as("service collection subtype replaced").isTrue();
				assertThat(ss1).as("wrong number of elements in service collection subtype").hasSize(0);
				Collection<ServiceReference<BaseService>> sr1 = t1.getCollectionSubtypeReference();
				assertThat(sr1).as("no service reference collection subtype").isNotNull();
				assertThat(sr1).as("wrong number of elements in service reference collection subtype").hasSize(0);
				Collection<ComponentServiceObjects<BaseService>> so1 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so1).as("no service objects collection subtype").isNotNull();
				assertThat(so1).as("wrong number of elements in service objects collection subtype").hasSize(0);
				Collection<Map<String, Object>> sp1 = t1.getCollectionSubtypeProperties();
				assertThat(sp1).as("no service properties collection subtype").isNotNull();
				assertThat(sp1 instanceof TestIdentitySet).as("service properties collection subtype not replaced").isTrue();
				assertThat(sp1).as("wrong number of elements in service properties collection subtype").hasSize(0);
				Collection<Entry<Map<String, Object>, BaseService>> st1 = t1.getCollectionSubtypeTuple();
				assertThat(st1).as("no service tuple collection subtype").isNotNull();
				assertThat(st1 instanceof TestIdentitySet).as("service tuple collection subtype not replaced").isTrue();
				assertThat(st1).as("wrong number of elements in service tuple collection subtype").hasSize(0);

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("missing test1").isNotNull();
				assertThat(t2).as("reactivated").isSameAs(t1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs2 = t1.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("service collection same").isNotSameAs(cs1);
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(1);
				Collection<ServiceReference<BaseService>> cr2 = t1.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("service reference collection same").isNotSameAs(cr1);
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(1);
				Collection<ComponentServiceObjects<BaseService>> co2 = t1.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("service objects collection same").isNotSameAs(co1);
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(1);
				Collection<Map<String, Object>> cp2 = t1.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection same").isNotSameAs(cp1);
				assertThat(cp2).as("wrong number of elements in service properties collection").hasSize(1);
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t1.getCollectionTuple();
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("service tuple collection same").isNotSameAs(ct1);
				assertThat(ct2).as("wrong number of elements in service tuple collection").hasSize(1);

				List<BaseService> ls2 = t1.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2).as("service list same").isNotSameAs(ls1);
				assertThat(ls2).as("wrong number of elements in service list").hasSize(1);
				List<ServiceReference<BaseService>> lr2 = t1.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2).as("service reference list same").isNotSameAs(lr1);
				assertThat(lr2).as("wrong number of elements in service reference list").hasSize(1);
				List<ComponentServiceObjects<BaseService>> lo2 = t1.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2).as("service objects list same").isNotSameAs(lo1);
				assertThat(lo2).as("wrong number of elements in service objects list").hasSize(1);
				List<Map<String, Object>> lp2 = t1.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list same").isNotSameAs(lp1);
				assertThat(lp2).as("wrong number of elements in service properties list").hasSize(1);
				List<Entry<Map<String, Object>, BaseService>> lt2 = t1.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list same").isNotSameAs(lt1);
				assertThat(lt2).as("wrong number of elements in service tuple list").hasSize(1);

				Collection<BaseService> ss2 = t1.getCollectionSubtypeService();
				assertThat(ss2).as("no service collection subtype").isNotNull();
				assertThat(ss2).as("service collection subtype not same").isSameAs(ss1);
				assertThat(ss2).as("wrong number of elements in service collection subtype").hasSize(1);
				Collection<ServiceReference<BaseService>> sr2 = t1.getCollectionSubtypeReference();
				assertThat(sr2).as("no service reference collection subtype").isNotNull();
				assertThat(sr2).as("service reference collection subtype not same").isSameAs(sr1);
				assertThat(sr2).as("wrong number of elements in service reference collection subtype").hasSize(1);
				Collection<ComponentServiceObjects<BaseService>> so2 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so2).as("no service objects collection subtype").isNotNull();
				assertThat(so2).as("service objects collection subtype not same").isSameAs(so1);
				assertThat(so2).as("wrong number of elements in service objects collection subtype").hasSize(1);
				Collection<Map<String, Object>> sp2 = t1.getCollectionSubtypeProperties();
				assertThat(sp2).as("no service properties collection subtype").isNotNull();
				assertThat(sp2).as("service properties collection subtype not same").isSameAs(sp1);
				assertThat(sp2).as("wrong number of elements in service properties collection subtype").hasSize(1);
				Collection<Entry<Map<String, Object>, BaseService>> st2 = t1.getCollectionSubtypeTuple();
				assertThat(st2).as("no service tuple collection subtype").isNotNull();
				assertThat(st2).as("service tuple collection subtype not same").isSameAs(st1);
				assertThat(st2).as("wrong number of elements in service tuple collection subtype").hasSize(1);

				p = scr.disableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

				c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("found comp1").isNull();
				MultipleFieldTestService<BaseService> t3 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t3).as("missing test1").isNotNull();
				assertThat(t3).as("reactivated").isSameAs(t1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs3 = t1.getCollectionService();
				assertThat(cs3).as("no service collection").isNotNull();
				assertThat(cs3).as("service collection same").isNotSameAs(cs1);
				assertThat(cs3).as("service collection same").isNotSameAs(cs2);
				assertThat(cs3).as("wrong number of elements in service collection").hasSize(0);
				Collection<ServiceReference<BaseService>> cr3 = t1.getCollectionReference();
				assertThat(cr3).as("no service reference collection").isNotNull();
				assertThat(cr3).as("service reference collection same").isNotSameAs(cr1);
				assertThat(cr3).as("service reference collection same").isNotSameAs(cr2);
				assertThat(cr3).as("wrong number of elements in service reference collection").hasSize(0);
				Collection<ComponentServiceObjects<BaseService>> co3 = t1.getCollectionServiceObjects();
				assertThat(co3).as("no service objects collection").isNotNull();
				assertThat(co3).as("service objects collection same").isNotSameAs(co1);
				assertThat(co3).as("service objects collection same").isNotSameAs(co2);
				assertThat(co3).as("wrong number of elements in service objects collection").hasSize(0);
				Collection<Map<String, Object>> cp3 = t1.getCollectionProperties();
				assertThat(cp3).as("no service properties collection").isNotNull();
				assertThat(cp3).as("service properties collection same").isNotSameAs(cp1);
				assertThat(cp3).as("service properties collection same").isNotSameAs(cp2);
				assertThat(cp3).as("wrong number of elements in service properties collection").hasSize(0);
				Collection<Entry<Map<String, Object>, BaseService>> ct3 = t1.getCollectionTuple();
				assertThat(ct3).as("no service tuple collection").isNotNull();
				assertThat(ct3).as("service tuple collection same").isNotSameAs(ct1);
				assertThat(ct3).as("service tuple collection same").isNotSameAs(ct2);
				assertThat(ct3).as("wrong number of elements in service tuple collection").hasSize(0);

				List<BaseService> ls3 = t1.getListService();
				assertThat(ls3).as("no service list").isNotNull();
				assertThat(ls3).as("service list same").isNotSameAs(ls1);
				assertThat(ls3).as("service list same").isNotSameAs(ls2);
				assertThat(ls3).as("wrong number of elements in service list").hasSize(0);
				List<ServiceReference<BaseService>> lr3 = t1.getListReference();
				assertThat(lr3).as("no service reference list").isNotNull();
				assertThat(lr3).as("service reference list same").isNotSameAs(lr1);
				assertThat(lr3).as("service reference list same").isNotSameAs(lr2);
				assertThat(lr3).as("wrong number of elements in service reference list").hasSize(0);
				List<ComponentServiceObjects<BaseService>> lo3 = t1.getListServiceObjects();
				assertThat(lo3).as("no service objects list").isNotNull();
				assertThat(lo3).as("service objects list same").isNotSameAs(lo1);
				assertThat(lo3).as("service objects list same").isNotSameAs(lo2);
				assertThat(lo3).as("wrong number of elements in service objects list").hasSize(0);
				List<Map<String, Object>> lp3 = t1.getListProperties();
				assertThat(lp3).as("no service properties list").isNotNull();
				assertThat(lp3).as("service properties list same").isNotSameAs(lp1);
				assertThat(lp3).as("service properties list same").isNotSameAs(lp2);
				assertThat(lp3).as("wrong number of elements in service properties list").hasSize(0);
				List<Entry<Map<String, Object>, BaseService>> lt3 = t1.getListTuple();
				assertThat(lt3).as("no service tuple list").isNotNull();
				assertThat(lt3).as("service tuple list same").isNotSameAs(lt1);
				assertThat(lt3).as("service tuple list same").isNotSameAs(lt2);
				assertThat(lt3).as("wrong number of elements in service tuple list").hasSize(0);

				Collection<BaseService> ss3 = t1.getCollectionSubtypeService();
				assertThat(ss3).as("no service collection subtype").isNotNull();
				assertThat(ss3).as("service collection subtype not same").isSameAs(ss1);
				assertThat(ss3).as("wrong number of elements in service collection subtype").hasSize(0);
				Collection<ServiceReference<BaseService>> sr3 = t1.getCollectionSubtypeReference();
				assertThat(sr3).as("no service reference collection subtype").isNotNull();
				assertThat(sr3).as("service reference collection subtype not same").isSameAs(sr1);
				assertThat(sr3).as("wrong number of elements in service reference collection subtype").hasSize(0);
				Collection<ComponentServiceObjects<BaseService>> so3 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so3).as("no service objects collection subtype").isNotNull();
				assertThat(so3).as("service objects collection subtype not same").isSameAs(so1);
				assertThat(so3).as("wrong number of elements in service objects collection subtype").hasSize(0);
				Collection<Map<String, Object>> sp3 = t1.getCollectionSubtypeProperties();
				assertThat(sp3).as("no service properties collection subtype").isNotNull();
				assertThat(sp3).as("service properties collection subtype not same").isSameAs(sp1);
				assertThat(sp3).as("wrong number of elements in service properties collection subtype").hasSize(0);
				Collection<Entry<Map<String, Object>, BaseService>> st3 = t1.getCollectionSubtypeTuple();
				assertThat(st3).as("no service tuple collection subtype").isNotNull();
				assertThat(st3).as("service tuple collection subtype not same").isSameAs(st1);
				assertThat(st3).as("wrong number of elements in service tuple collection subtype").hasSize(0);

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testDynamicMultipleFieldReferenceModified130() throws Exception {
		ConfigurationAdmin cm = trackerCM.getService();
		assertThat(cm).as("The ConfigurationAdmin should be available").isNotNull();
		final String PID_ROOT = TEST_CASE_ROOT + ".tb24";

		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicMultipleFieldReceiver";

		try {
			tb24.start();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isEqualTo(1);
				assertThat(t1.getModificationCount()).as("wrong modification count").isEqualTo(0);
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isEqualTo(0);

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1.size()).as("wrong number of elements in service collection").isEqualTo(1);
				assertThat(cs1.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1.size()).as("wrong number of elements in service reference collection").isEqualTo(1);
				assertThat(cr1.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1.size()).as("wrong number of elements in service objects collection").isEqualTo(1);
				assertThat(co1.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				Map<String, Object> properties = cp1.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1.size()).as("wrong number of elements in service tuple collection").isEqualTo(1);
				Map<String, Object> key = ct1.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1.size()).as("wrong number of elements in service list").isEqualTo(1);
				assertThat(ls1.get(0)).as("wrong service in service list").isSameAs(c1);
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1.size()).as("wrong number of elements in service reference list").isEqualTo(1);
				assertThat(lr1.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1.size()).as("wrong number of elements in service objects list").isEqualTo(1);
				assertThat(lo1.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp1.get(0);
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1.size()).as("wrong number of elements in service tuple list").isEqualTo(1);
				key = lt1.get(0).getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				Collection<BaseService> ss1 = t1.getCollectionSubtypeService();
				assertThat(ss1).as("no service collection subtype").isNotNull();
				assertThat(ss1.size()).as("wrong number of elements in service collection subtype").isEqualTo(1);
				assertThat(ss1.iterator().next()).as("wrong service in service collection subtype").isSameAs(c1);
				Collection<ServiceReference<BaseService>> sr1 = t1.getCollectionSubtypeReference();
				assertThat(sr1).as("no service reference collection subtype").isNotNull();
				assertThat(sr1.size()).as("wrong number of elements in service reference collection subtype").isEqualTo(1);
				assertThat(sr1.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<ComponentServiceObjects<BaseService>> so1 = t1.getCollectionSubtypeServiceObjects();
				assertThat(so1).as("no service objects collection subtype").isNotNull();
				assertThat(so1.size()).as("wrong number of elements in service objects collection subtype").isEqualTo(1);
				assertThat(so1.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Map<String, Object>> sp1 = t1.getCollectionSubtypeProperties();
				assertThat(sp1).as("no service properties collection subtype").isNotNull();
				assertThat(sp1.size()).as("wrong number of elements in service properties collection subtype").isEqualTo(1);
				properties = sp1.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");
				Collection<Entry<Map<String, Object>, BaseService>> st1 = t1.getCollectionSubtypeTuple();
				assertThat(st1).as("no service tuple collection subtype").isNotNull();
				assertThat(st1.size()).as("wrong number of elements in service tuple collection subtype").isEqualTo(1);
				key = st1.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("xml");

				Configuration config = cm.getConfiguration("org.osgi.test.cases.component.tb24.Ranking1", null);
				Dictionary<String, Object> props = new Hashtable<String, Object>();
				props.put(PID_ROOT, "config");
				config.update(props);
				settle(SLEEP * 3); // wait for SCR to react to change

				assertThat(c1.getProperties().get(PID_ROOT)).as("wrong service property value").isEqualTo("config");
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("test service reactivated").isSameAs(t1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs2 = t2.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(1);
				assertThat(cs2.iterator().next()).as("wrong service in service collection").isSameAs(c1);
				Collection<ServiceReference<BaseService>> cr2 = t2.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(1);
				assertThat(cr2.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<ComponentServiceObjects<BaseService>> co2 = t2.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(1);
				assertThat(co2.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Map<String, Object>> cp2 = t2.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection not replaced").isNotSameAs(sp1);
				assertThat(cp2.size()).as("wrong number of elements in service properties collection").isEqualTo(1);
				properties = cp2.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t2.getCollectionTuple();
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("service tuple collection not replaced").isNotSameAs(st1);
				assertThat(ct2.size()).as("wrong number of elements in service tuple collection").isEqualTo(1);
				key = ct2.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");

				List<BaseService> ls2 = t2.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2.size()).as("wrong number of elements in service list").isEqualTo(1);
				assertThat(ls2.get(0)).as("wrong service in service list").isSameAs(c1);
				List<ServiceReference<BaseService>> lr2 = t2.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2.size()).as("wrong number of elements in service reference list").isEqualTo(1);
				assertThat(lr2.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<ComponentServiceObjects<BaseService>> lo2 = t2.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2.size()).as("wrong number of elements in service objects list").isEqualTo(1);
				assertThat(lo2.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<Map<String, Object>> lp2 = t2.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp2.size()).as("wrong number of elements in service properties list").isEqualTo(1);
				properties = lp2.get(0);
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				List<Entry<Map<String, Object>, BaseService>> lt2 = t2.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt2.size()).as("wrong number of elements in service tuple list").isEqualTo(1);
				key = lt2.get(0).getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");

				Collection<BaseService> ss2 = t2.getCollectionSubtypeService();
				assertThat(ss2).as("no service collection subtype").isNotNull();
				assertThat(ss2).as("service collection subtype replaced").isSameAs(ss1);
				assertThat(ss2).as("wrong number of elements in service collection subtype").hasSize(1);
				assertThat(((TestSet<BaseService>) ss2).getOps()).as("wrong collection operations on service collection subtype").hasToString("add");
				assertThat(ss2.iterator().next()).as("wrong service in service collection subtype").isSameAs(c1);
				Collection<ServiceReference<BaseService>> sr2 = t2.getCollectionSubtypeReference();
				assertThat(sr2).as("no service reference collection subtype").isNotNull();
				assertThat(sr2).as("service reference collection subtype replaced").isSameAs(sr1);
				assertThat(sr2).as("wrong number of elements in service reference collection subtype").hasSize(1);
				assertThat(sr2.iterator().next().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<ComponentServiceObjects<BaseService>> so2 = t2.getCollectionSubtypeServiceObjects();
				assertThat(so2).as("no service objects collection subtype").isNotNull();
				assertThat(so2).as("service objects collection subtype replaced").isSameAs(so1);
				assertThat(so2).as("wrong number of elements in service objects collection subtype").hasSize(1);
				assertThat(so2.iterator().next().getServiceReference().getProperty(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Map<String, Object>> sp2 = t2.getCollectionSubtypeProperties();
				assertThat(sp2).as("no service properties collection subtype").isNotNull();
				assertThat(sp2).as("service properties collection subtype replaced").isSameAs(sp1);
				assertThat(sp2.size()).as("wrong number of elements in service properties collection subtype").isEqualTo(1);
				assertThat(((TestIdentitySet<Map<String, Object>>) sp2).getOps().toString()).as("wrong collection operations on service properties collection subtype").isEqualTo("addaddremove");
				properties = sp2.iterator().next();
				assertThat(properties).as("no properties injected").isNotNull();
				assertThat(properties.get(PID_ROOT)).as("wrong property value").isEqualTo("config");
				Collection<Entry<Map<String, Object>, BaseService>> st2 = t2.getCollectionSubtypeTuple();
				assertThat(st2).as("no service tuple collection subtype").isNotNull();
				assertThat(st2).as("service tuple collection subtype replaced").isSameAs(st1);
				assertThat(st2.size()).as("wrong number of elements in service tuple collection subtype").isEqualTo(1);
				assertThat(((TestIdentitySet<Entry<Map<String, Object>, BaseService>>) st2).getOps().toString()).as("wrong collection operations on service tuple collection subtype").isEqualTo("addaddremove");
				key = st2.iterator().next().getKey();
				assertThat(key).as("no tuple injected").isNotNull();
				assertThat(key.get(PID_ROOT)).as("wrong property value").isEqualTo("config");

			}
			finally {
				comp1Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	@Test
	public void testSortingDynamicMultipleFieldReference130() throws Exception {
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb24 = installer.installBundle(context.getBundle().getEntry("tb24.jar"), false);
		assertThat(tb24).as("tb24 failed to install").isNotNull();

		final String NAME = TEST_CASE_ROOT + ".tb24.DynamicMultipleFieldReceiver";

		try {
			tb24.start();

			ComponentDescriptionDTO description1 = scr.getComponentDescriptionDTO(tb24,
					"org.osgi.test.cases.component.tb24.Ranking10");
			assertThat(description1).as("null description1").isNotNull();
			assertThat(scr.isComponentEnabled(description1)).as("description1 is enabled").isFalse();

			Filter comp1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking1))");
			Filter comp10Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + BaseService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=org.osgi.test.cases.component.tb24.Ranking10))");
			Filter test1Filter = context
					.createFilter("(&(" + Constants.OBJECTCLASS + "=" + MultipleFieldTestService.class.getName() + ")("
							+ ComponentConstants.COMPONENT_NAME + "=" + NAME + "))");
			ServiceTracker<BaseService, BaseService> comp1Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp1Filter, null);
			ServiceTracker<BaseService, BaseService> comp10Tracker = new ServiceTracker<BaseService, BaseService>(
					context, comp10Filter, null);
			ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>> test1Tracker = new ServiceTracker<MultipleFieldTestService<BaseService>, MultipleFieldTestService<BaseService>>(
					context, test1Filter, null);
			try {
				comp1Tracker.open();
				comp10Tracker.open();
				test1Tracker.open();
				BaseService c1 = comp1Tracker.waitForService(SLEEP * 3);
				assertThat(c1).as("missing comp1").isNotNull();
				BaseService c10 = comp10Tracker.waitForService(SLEEP * 3);
				assertThat(c10).as("found comp10").isNull();
				MultipleFieldTestService<BaseService> t1 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t1).as("missing test1").isNotNull();
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs1 = t1.getCollectionService();
				assertThat(cs1).as("no service collection").isNotNull();
				assertThat(cs1).as("wrong number of elements in service collection").hasSize(1);
				Collection<ServiceReference<BaseService>> cr1 = t1.getCollectionReference();
				assertThat(cr1).as("no service reference collection").isNotNull();
				assertThat(cr1).as("wrong number of elements in service reference collection").hasSize(1);
				Collection<ComponentServiceObjects<BaseService>> co1 = t1.getCollectionServiceObjects();
				assertThat(co1).as("no service objects collection").isNotNull();
				assertThat(co1).as("wrong number of elements in service objects collection").hasSize(1);
				Collection<Map<String, Object>> cp1 = t1.getCollectionProperties();
				assertThat(cp1).as("no service properties collection").isNotNull();
				assertThat(cp1).as("wrong number of elements in service properties collection").hasSize(1);
				Collection<Entry<Map<String, Object>, BaseService>> ct1 = t1.getCollectionTuple();
				assertThat(ct1).as("no service tuple collection").isNotNull();
				assertThat(ct1).as("wrong number of elements in service tuple collection").hasSize(1);

				List<BaseService> ls1 = t1.getListService();
				assertThat(ls1).as("no service list").isNotNull();
				assertThat(ls1).as("wrong number of elements in service list").hasSize(1);
				List<ServiceReference<BaseService>> lr1 = t1.getListReference();
				assertThat(lr1).as("no service reference list").isNotNull();
				assertThat(lr1).as("wrong number of elements in service reference list").hasSize(1);
				List<ComponentServiceObjects<BaseService>> lo1 = t1.getListServiceObjects();
				assertThat(lo1).as("no service objects list").isNotNull();
				assertThat(lo1).as("wrong number of elements in service objects list").hasSize(1);
				List<Map<String, Object>> lp1 = t1.getListProperties();
				assertThat(lp1).as("no service properties list").isNotNull();
				assertThat(lp1).as("wrong number of elements in service properties list").hasSize(1);
				List<Entry<Map<String, Object>, BaseService>> lt1 = t1.getListTuple();
				assertThat(lt1).as("no service tuple list").isNotNull();
				assertThat(lt1).as("wrong number of elements in service tuple list").hasSize(1);

				Promise<Void> p = scr.enableComponent(description1);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(description1)).as("description1 is not enabled").isTrue();

				c10 = comp10Tracker.waitForService(SLEEP * 3);
				assertThat(c10).as("missing comp10").isNotNull();
				MultipleFieldTestService<BaseService> t2 = test1Tracker.waitForService(SLEEP * 3);
				assertThat(t2).as("missing test1").isNotNull();
				assertThat(t2).as("reactivated").isSameAs(t1);
				assertThat(t1.getActivationCount()).as("wrong activation count").isOne();
				assertThat(t1.getModificationCount()).as("wrong modification count").isZero();
				assertThat(t1.getDeactivationCount()).as("wrong deactivation count").isZero();

				Collection<BaseService> cs2 = t1.getCollectionService();
				assertThat(cs2).as("no service collection").isNotNull();
				assertThat(cs2).as("service collection not replaced").isNotSameAs(cs1);
				assertThat(cs2).as("wrong number of elements in service collection").hasSize(2);
				assertThat(cs2).as("wrong order in service collection").isEqualTo(Arrays.asList(c1, c10));
				Collection<ServiceReference<BaseService>> cr2 = t1.getCollectionReference();
				assertThat(cr2).as("no service reference collection").isNotNull();
				assertThat(cr2).as("service reference collection not replaced").isNotSameAs(cr1);
				assertThat(cr2).as("wrong number of elements in service reference collection").hasSize(2);
				assertThat(cr2).as("wrong order in service reference collection").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				Collection<ComponentServiceObjects<BaseService>> co2 = t1.getCollectionServiceObjects();
				assertThat(co2).as("no service objects collection").isNotNull();
				assertThat(co2).as("service objects collection not replaced").isNotSameAs(co1);
				assertThat(co2).as("wrong number of elements in service objects collection").hasSize(2);
				Iterator<ComponentServiceObjects<BaseService>> co2i = co2.iterator();
				assertThat(Arrays.asList(co2i.next().getServiceReference(), co2i.next().getServiceReference())).as("wrong order in service objects collection").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				Collection<Map<String, Object>> cp2 = t1.getCollectionProperties();
				assertThat(cp2).as("no service properties collection").isNotNull();
				assertThat(cp2).as("service properties collection not replaced").isNotSameAs(cp1);
				assertThat(cp2).as("wrong number of elements in service properties collection").hasSize(2);
				Iterator<Map<String, Object>> cp2i = cp2.iterator();
				assertThat(asComparable(cp2i.next()).compareTo(cp2i.next()) < 0).as("wrong order in service properties collection").isTrue();
				Collection<Entry<Map<String, Object>, BaseService>> ct2 = t1.getCollectionTuple();
				assertThat(ct2).as("no service tuple collection").isNotNull();
				assertThat(ct2).as("service tuple collection not replaced").isNotSameAs(ct1);
				assertThat(ct2).as("wrong number of elements in service tuple collection").hasSize(2);
				Iterator<Entry<Map<String, Object>, BaseService>> ct2i = ct2.iterator();
				assertThat(asComparable(ct2i.next()).compareTo(ct2i.next()) < 0).as("wrong order in service tuple collection").isTrue();

				List<BaseService> ls2 = t1.getListService();
				assertThat(ls2).as("no service list").isNotNull();
				assertThat(ls2).as("service list not replaced").isNotSameAs(ls1);
				assertThat(ls2).as("wrong number of elements in service list").hasSize(2);
				assertThat(ls2).as("wrong order in service list").isEqualTo(Arrays.asList(c1, c10));
				List<ServiceReference<BaseService>> lr2 = t1.getListReference();
				assertThat(lr2).as("no service reference list").isNotNull();
				assertThat(lr2).as("service reference list not replaced").isNotSameAs(lr1);
				assertThat(lr2).as("wrong number of elements in service reference list").hasSize(2);
				assertThat(lr2).as("wrong order in service reference list").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				List<ComponentServiceObjects<BaseService>> lo2 = t1.getListServiceObjects();
				assertThat(lo2).as("no service objects list").isNotNull();
				assertThat(lo2).as("service objects list not replaced").isNotSameAs(lo1);
				assertThat(lo2).as("wrong number of elements in service objects list").hasSize(2);
				assertThat(Arrays.asList(lo2.get(0).getServiceReference(), lo2.get(1).getServiceReference())).as("wrong order in service objects list").isEqualTo(Arrays.asList(comp1Tracker.getServiceReference(), comp10Tracker.getServiceReference()));
				List<Map<String, Object>> lp2 = t1.getListProperties();
				assertThat(lp2).as("no service properties list").isNotNull();
				assertThat(lp2).as("service properties list not replaced").isNotSameAs(lp1);
				assertThat(lp2).as("wrong number of elements in service properties list").hasSize(2);
				assertThat(asComparable(lp2.get(0)).compareTo(lp2.get(1)) < 0).as("wrong order in service properties list").isTrue();
				List<Entry<Map<String, Object>, BaseService>> lt2 = t1.getListTuple();
				assertThat(lt2).as("no service tuple list").isNotNull();
				assertThat(lt2).as("service tuple list not replaced").isNotSameAs(lt1);
				assertThat(lt2).as("wrong number of elements in service tuple list").hasSize(2);
				assertThat(asComparable(lt2.get(0)).compareTo(lt2.get(1)) < 0).as("wrong order in service tuple list").isTrue();
			}
			finally {
				comp1Tracker.close();
				comp10Tracker.close();
				test1Tracker.close();
			}
		}
		finally {
			tb24.uninstall();
		}
	}

	static class BaseServiceImpl implements BaseService {
		public Dictionary<String, Object> getProperties() {
			return null;
		}
	}

	@Test
	public void testComponentServiceObjectsSingleton130() throws Exception {
		trackerBaseService.close(); // Don't mess up BaseService count!
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb25 = installer.installBundle(context.getBundle().getEntry("tb25.jar"), false);
		assertThat(tb25).as("tb25 failed to install").isNotNull();

		Dictionary<String, Object> baseProps = new Hashtable<String, Object>();
		baseProps.put(ComponentConstants.COMPONENT_NAME, "org.osgi.test.cases.component.tb25.BaseService");
		BaseService b1 = new BaseServiceImpl();
		ServiceRegistration<BaseService> baseReg = context.registerService(BaseService.class, b1, baseProps);
		try {
			tb25.start();

			ComponentDescriptionDTO receiverDesc = scr.getComponentDescriptionDTO(tb25,
					"org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver");
			assertThat(receiverDesc).as("null receiverDesc").isNotNull();
			assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is not enabled").isTrue();

			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver))");
			ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>> receiverTracker = new ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>>(
					context, receiverFilter, null);
			try {
				assertThat(baseReg.getReference().getUsingBundles()).as("wrong number of services").isNull();

				receiverTracker.open();
				ServiceReceiver<ComponentServiceObjects<BaseService>> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				ComponentServiceObjects<BaseService> cso = r.getServices().get(0);
				assertThat(cso).as("missing ComponentServiceObjects").isNotNull();
				assertThat(cso.getServiceReference()).as("wrong service reference").isEqualTo(baseReg.getReference());

				assertThat(b1).as("missing base").isNotNull();
				for (int i = 0; i < 12; i++) {
					assertThat(cso.getService()).as("wrong service").isSameAs(b1);
				}
				assertThat(baseReg.getReference().getUsingBundles().length).as("wrong number of services").isOne();

				assertThatExceptionOfType(IllegalArgumentException.class).as("unget of invalid service succeeded" + " expected:[" + IllegalArgumentException.class.getName() + "] and got nothing")
						.isThrownBy(() -> cso.ungetService(mock(BaseService.class)));
				for (int i = 0; i < 12; i++) {
					cso.ungetService(b1);
				}
				assertThat(baseReg.getReference().getUsingBundles()).as("wrong number of services").isNull();
				b1 = cso.getService();
				assertThat(b1).as("missing base").isNotNull();
				for (int i = 0; i < 11; i++) {
					assertThat(cso.getService()).as("wrong service").isSameAs(b1);
				}
				assertThat(baseReg.getReference().getUsingBundles().length).as("wrong number of services").isOne();

				baseProps.put(ComponentConstants.COMPONENT_NAME, "unbind");
				baseReg.setProperties(baseProps);
				settle(SLEEP * 3); // wait for SCR to react to change
				assertThat(r.getServices().get(0)).as("base not unbound").isNull();
				assertThat(baseReg.getReference().getUsingBundles()).as("wrong number of services").isNull();

				assertThat(cso.getService()).as("returned service object for unbound service").isNull();

				Promise<Void> p = scr.disableComponent(receiverDesc);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is enabled").isFalse();
				assertThatExceptionOfType(IllegalStateException.class).as("get on deactivated component succeeded" + " expected:[" + IllegalStateException.class.getName() + "] and got nothing")
						.isThrownBy(() -> cso.getService());
			}
			finally {
				receiverTracker.close();
			}
		}
		finally {
			tb25.uninstall();
			baseReg.unregister();
		}
	}

	static class BaseServiceServiceFactory implements ServiceFactory<BaseService> {
		final AtomicInteger counter;

		public BaseServiceServiceFactory(AtomicInteger counter) {
			this.counter = counter;
		}

		public BaseService getService(Bundle bundle, ServiceRegistration<BaseService> registration) {
			counter.incrementAndGet();
			BaseService service = new BaseServiceImpl();
			System.out.printf("getService: %s[%X]\n",
					registration.getReference().getProperty(ComponentConstants.COMPONENT_NAME),
					System.identityHashCode(service));
			return service;
		}

		public void ungetService(Bundle bundle, ServiceRegistration<BaseService> registration, BaseService service) {
			if (service instanceof BaseServiceImpl) {
				System.out.printf("ungetService: %s[%X]\n",
						registration.getReference().getProperty(ComponentConstants.COMPONENT_NAME),
						System.identityHashCode(service));
				counter.decrementAndGet();
			}
		}
	}

	@Test
	public void testComponentServiceObjectsBundle130() throws Exception {
		trackerBaseService.close(); // Don't mess up BaseService count!
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb25 = installer.installBundle(context.getBundle().getEntry("tb25.jar"), false);
		assertThat(tb25).as("tb25 failed to install").isNotNull();

		Dictionary<String, Object> baseProps = new Hashtable<String, Object>();
		baseProps.put(ComponentConstants.COMPONENT_NAME, "org.osgi.test.cases.component.tb25.BaseService");
		AtomicInteger counter = new AtomicInteger();
		BaseServiceServiceFactory f1 = new BaseServiceServiceFactory(counter);
		ServiceRegistration<BaseService> baseReg = context.registerService(BaseService.class, f1, baseProps);
		try {
			tb25.start();

			ComponentDescriptionDTO receiverDesc = scr.getComponentDescriptionDTO(tb25,
					"org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver");
			assertThat(receiverDesc).as("null receiverDesc").isNotNull();
			assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is not enabled").isTrue();

			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver))");
			ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>> receiverTracker = new ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>>(
					context, receiverFilter, null);
			try {
				assertThat(counter.get()).as("wrong number of services").isZero();

				receiverTracker.open();
				ServiceReceiver<ComponentServiceObjects<BaseService>> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				ComponentServiceObjects<BaseService> cso = r.getServices().get(0);
				assertThat(cso).as("missing ComponentServiceObjects").isNotNull();
				assertThat(cso.getServiceReference()).as("wrong service reference").isEqualTo(baseReg.getReference());

				BaseService b1 = cso.getService();
				assertThat(b1).as("missing base").isNotNull();
				for (int i = 0; i < 11; i++) {
					assertThat(cso.getService()).as("wrong service").isSameAs(b1);
				}
				assertThat(counter.get()).as("wrong number of services").isOne();
				assertThatExceptionOfType(IllegalArgumentException.class).as("unget of invalid service succeeded" + " expected:[" + IllegalArgumentException.class.getName() + "] and got nothing")
						.isThrownBy(() -> cso.ungetService(mock(BaseService.class)));
				for (int i = 0; i < 12; i++) {
					cso.ungetService(b1);
				}

				assertThat(counter.get()).as("wrong number of services").isZero();
				b1 = cso.getService();
				assertThat(b1).as("missing base").isNotNull();
				for (int i = 0; i < 11; i++) {
					assertThat(cso.getService()).as("wrong service").isSameAs(b1);
				}
				assertThat(counter.get()).as("wrong number of services").isOne();

				baseProps.put(ComponentConstants.COMPONENT_NAME, "unbind");
				baseReg.setProperties(baseProps);
				settle(SLEEP * 3); // wait for SCR to react to change
				assertThat(r.getServices().get(0)).as("base not unbound").isNull();
				assertThat(counter.get()).as("wrong number of services").isZero();

				assertThat(cso.getService()).as("returned service object for unbound service").isNull();

				Promise<Void> p = scr.disableComponent(receiverDesc);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is enabled").isFalse();

				assertThatExceptionOfType(IllegalStateException.class).as("get on deactivated component succeeded" + " expected:[" + IllegalStateException.class.getName() + "] and got nothing")
						.isThrownBy(() -> cso.getService());
			}
			finally {
				receiverTracker.close();
			}
		}
		finally {
			tb25.uninstall();
			baseReg.unregister();
		}
	}

	static class BaseServicePrototypeServiceFactory extends BaseServiceServiceFactory
			implements PrototypeServiceFactory<BaseService> {
		public BaseServicePrototypeServiceFactory(AtomicInteger counter) {
			super(counter);
		}
	}

	@Test
	public void testComponentServiceObjectsPrototype130() throws Exception {
		trackerBaseService.close(); // Don't mess up BaseService count!
		ServiceComponentRuntime scr = scrTracker.getService();
		assertThat(scr).as("failed to find ServiceComponentRuntime service").isNotNull();
		Bundle tb25 = installer.installBundle(context.getBundle().getEntry("tb25.jar"), false);
		assertThat(tb25).as("tb25 failed to install").isNotNull();

		Dictionary<String, Object> baseProps = new Hashtable<String, Object>();
		baseProps.put(ComponentConstants.COMPONENT_NAME, "org.osgi.test.cases.component.tb25.BaseService");
		AtomicInteger counter = new AtomicInteger();
		BaseServicePrototypeServiceFactory f1 = new BaseServicePrototypeServiceFactory(counter);
		ServiceRegistration<BaseService> baseReg = context.registerService(BaseService.class, f1, baseProps);
		try {
			tb25.start();

			ComponentDescriptionDTO receiverDesc = scr.getComponentDescriptionDTO(tb25,
					"org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver");
			assertThat(receiverDesc).as("null receiverDesc").isNotNull();
			assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is not enabled").isTrue();

			Filter receiverFilter = context.createFilter("(&(" + Constants.OBJECTCLASS + "="
					+ ServiceReceiver.class.getName() + ")(" + ComponentConstants.COMPONENT_NAME
					+ "=org.osgi.test.cases.component.tb25.ComponentServiceObjectsReceiver))");
			ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>> receiverTracker = new ServiceTracker<ServiceReceiver<ComponentServiceObjects<BaseService>>, ServiceReceiver<ComponentServiceObjects<BaseService>>>(
					context, receiverFilter, null);
			try {
				assertThat(counter.get()).as("wrong number of services").isZero();

				receiverTracker.open();
				ServiceReceiver<ComponentServiceObjects<BaseService>> r = receiverTracker.waitForService(SLEEP * 3);
				assertThat(r).as("missing receiver").isNotNull();
				ComponentServiceObjects<BaseService> cso = r.getServices().get(0);
				assertThat(cso).as("missing ComponentServiceObjects").isNotNull();
				assertThat(cso.getServiceReference()).as("wrong service reference").isEqualTo(baseReg.getReference());

				List<BaseService> services = new ArrayList<BaseService>(12);
				for (int i = 0; i < 12; i++) {
					BaseService b1 = cso.getService();
					assertThat(b1).as("missing base").isNotNull();
					assertThat(services.contains(b1)).as("wrong service").isFalse();
					services.add(b1);
				}
				assertThat(counter.get()).as("wrong number of services").isEqualTo(12);
				try {
					cso.ungetService(mock(BaseService.class));
					fail("unget of invalid service succeeded" + " expected:[" + IllegalArgumentException.class.getName() + "] and got nothing");
				}
				catch (IllegalArgumentException e) {
					// expected
				}
				for (BaseService b1 : services) {
					cso.ungetService(b1);
				}

				assertThat(counter.get()).as("wrong number of services").isZero();
				services = new ArrayList<BaseService>(12);
				for (int i = 0; i < 12; i++) {
					BaseService b1 = cso.getService();
					assertThat(b1).as("missing base").isNotNull();
					assertThat(services.contains(b1)).as("wrong service").isFalse();
					services.add(b1);
				}
				assertThat(counter.get()).as("wrong number of services").isEqualTo(12);

				baseProps.put(ComponentConstants.COMPONENT_NAME, "unbind");
				baseReg.setProperties(baseProps);
				settle(SLEEP * 3); // wait for SCR to react to change
				assertThat(r.getServices().get(0)).as("base not unbound").isNull();
				assertThat(counter.get()).as("wrong number of services").isZero();

				assertThat(cso.getService()).as("returned service object for unbound service").isNull();

				Promise<Void> p = scr.disableComponent(receiverDesc);
				p.getValue(); // wait for state change to complete
				assertThat(scr.isComponentEnabled(receiverDesc)).as("receiverDesc is enabled").isFalse();

				assertThatExceptionOfType(IllegalStateException.class).as("get on deactivated component succeeded" + " expected:[" + IllegalStateException.class.getName() + "] and got nothing")
						.isThrownBy(() -> cso.getService());
			}
			finally {
				receiverTracker.close();
			}
		}
		finally {
			tb25.uninstall();
			baseReg.unregister();
		}
	}

	/**
	 * Waits for the framework to settle instead of sleeping a fixed time:
	 * returns once no bundle or service event has arrived for
	 * SETTLE_QUIET_MILLIS, and at the latest after maxMillis, as the former
	 * sleep did.
	 */
	void settle(long maxMillis) throws InterruptedException {
		Duration quiet = Duration.ofMillis(Math.min(SETTLE_QUIET_MILLIS, Math.max(1, maxMillis / 2)));
		try {
			awaitCalm.waitForQuiet(quiet, Duration.ofMillis(maxMillis));
		} catch (AwaitCalmTimeoutException e) {
			// still busy after maxMillis: go on, as the fixed sleep did
		}
	}
}
