// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('test_name');

// Assuming that sequencers is a function that returns an instance of SequencerBuilderJS
let sequencerBuilder = applicationBuilder.sequencers;

// Test random method
try {
    let randomResult = sequencerBuilder.random();
    console.log('random method executed successfully. Result:', randomResult);
} catch (error) {
    console.error('Error when executing random method:', error);
}
