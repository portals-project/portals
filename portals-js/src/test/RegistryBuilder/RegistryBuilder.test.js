let applicationBuilder = PortalsJS.ApplicationBuilder('RegistryBuilder');

// Assuming that registry is a function that returns an instance of RegistryBuilderJS
let registryBuilder = applicationBuilder.registry;

// Test sequencers method
try {
    let sequencersResult = registryBuilder.sequencers;
    console.log('sequencers method executed successfully. Result:', sequencersResult);
} catch (error) {
    console.error('Error when executing sequencers method:', error);
}

// Test splitters method
try {
    let splittersResult = registryBuilder.splitters;
    console.log('splitters method executed successfully. Result:', splittersResult);
} catch (error) {
    console.error('Error when executing splitters method:', error);
}

// Test streams method
try {
    let streamsResult = registryBuilder.streams;
    console.log('streams method executed successfully. Result:', streamsResult);
} catch (error) {
    console.error('Error when executing streams method:', error);
}

// Test portals method
try {
    let portalsResult = registryBuilder.portals;
    console.log('portals method executed successfully. Result:', portalsResult);
} catch (error) {
    console.error('Error when executing portals method:', error);
}
