# Mobile Phone Virus Simulation

A Java project that shows multithreading through a virus spread simulation. Phones bounce around, viruses spread when they get close, and there's a repair shop to fix them. All in 5 seconds

## Demo Video

![Demo Animation](https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExZWdzMDNuZ2x6bmhtdHNianJnbWdtdmxlOW1tN2xleGdmMWx6c2p4cSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/t1q7R72BYpdZelvZgS/giphy.gif)

## What's This About?

I needed a way to demonstrate concurrent programming for my portfolio, so I built this simulation. Each phone is its own thread bouncing around the screen. When an infected phone gets near a healthy one, it spreads the virus. Infected phones lose health over time and eventually head to the repair shop.

The whole thing runs smoothly with dozens of phones because I used proper thread management instead of just spawning threads everywhere.

## Key Features

### The Threading Stuff
- **Thread Pools**: Used ExecutorService instead of creating threads manually
- **Lock-Free Operations**: ConcurrentLinkedQueue and atomic variables prevent most synchronization headaches
- **Background Processing**: Virus calculations and stats run on separate scheduled threads
- **Pause/Resume**: Can stop and start all phone threads without breaking anything

### Visual Elements
- **Images**: Real PNG files for different phone states (way better than colored dots)
- **Live Stats**: Shows infection counts and active thread count
- **Timeline Graph**: Tracks infection spread over time
- **Health Indicators**: Infected phones show how much health they have left

### Simulation Logic
- **Proximity Infection**: Viruses spread when phones get within a certain distance
- **Single Repair Shop**: Only one phone can be repaired at a time (realistic bottleneck)
- **State Machine**: Phones go from healthy → infected → seeking repair → fixed
- **Boundary Physics**: Phones bounce off walls instead of disappearing

## Requirements

- Java 8+
- Screen resolution of at least 1200x800

## Project Layout

```
VirusSim/
├── src/
│   ├── VirusSimulation.java    # Main entry point
│   ├── Panel.java              # Main simulation panel
│   ├── Phone.java              # Individual phone (each runs in its own thread)
│   └── RepairShop.java         # The repair facility
├── Images/                     # Phone state images
├── bin/                        # Compiled classes
└── README.md
```

## Building and Running

Compile everything:
```bash
cd VirusSim
javac -d bin src/*.java
```

Run the simulation:
```bash
java -cp bin VirusSimulation
```

## Controls

- **↑ Arrow** or **Add Phone button**: Spawn a new phone
- **V** or **Infect Random button**: Infect a random healthy phone
- **Spacebar** or **Pause button**: Pause/unpause the simulation

## Phone States
- **Blue**: Healthy
- **Red**: Infected (losing health)
- **Orange - its more dark orange**: Heading to repair shop
- **Green**: Fixed and immune

## Technical Details

This project showcases several concurrent programming patterns:

- **Compare-and-Swap**: Thread-safe state changes without explicit locking
- **Atomic Variables**: Statistics tracking without synchronization bottlenecks
- **Resource Management**: The repair shop handles concurrent access properly
- **Thread Coordination**: Pause/resume works across all threads without deadlocks
- **Memory Visibility**: Volatile variables ensure changes are seen across threads

## Performance

- Handles 100+ concurrent phone threads without issues
- 60fps rendering with efficient collision detection
- Dead phones get cleaned up automatically
- Thread pool prevents the overhead of constant thread creation/destruction

## Common Issues

- **Keyboard not working?** Click the simulation area to give it focus
- **No images?** Check that the Images/ folder is in the project root
- **Laggy performance?** You might have too many phones or a thread leak

## Why I Made This

I wanted something visual to demonstrate threading concepts in interviews. It's more interesting than explaining producer-consumer patterns on a whiteboard, and it actually uses the same patterns you'd find in real applications.
