// Instantiate the SystemJS object
let system = PortalsJS.System();

// Assuming that Application object can be obtained from ApplicationBuilder
let applicationBuilder = PortalsJS.ApplicationBuilder('System');
let application = applicationBuilder.build();

// Test registry method
try {
    let registryResult = system.registry;
    console.log('registry method executed successfully. Result:', registryResult);
} catch (error) {
    console.error('Error when executing registry method:', error);
}

// Test launch method
try {
    system.launch(application);
    console.log('launch method executed successfully.');
} catch (error) {
    console.error('Error when executing launch method:', error);
}

// Test step method
try {
    system.step();
    console.log('step method executed successfully.');
} catch (error) {
    console.error('Error when executing step method:', error);
}

// Test stepUntilComplete method
try {
    system.stepUntilComplete();
    console.log('stepUntilComplete method executed successfully.');
} catch (error) {
    console.error('Error when executing stepUntilComplete method:', error);
}

// Test shutdown method
try {
    system.shutdown();
    console.log('shutdown method executed successfully.');
} catch (error) {
    console.error('Error when executing shutdown method:', error);
}
