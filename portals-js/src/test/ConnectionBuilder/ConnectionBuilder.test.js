// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('test_name');

// Assuming that connections is a function that returns an instance of ConnectionBuilderJS
let connectionBuilder = applicationBuilder.connections;

// Assuming that we have some AtomicStreamRefKind and AtomicSequencerRefKind objects
let from = applicationBuilder.generators.fromRange(0, 128, 8).stream; // AtomicStreamRefKind instance
let to = {}; // AtomicSequencerRefKind instance

// Test connect method
try {
    connectionBuilder.connect(from, to);
    console.log('connect method executed successfully.');
} catch (error) {
    console.error('Error when executing connect method:', error);
}
