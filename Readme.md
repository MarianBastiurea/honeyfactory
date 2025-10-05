HoneyFactory — using Virtual Threads, Structured Concurrency, and AWS Free Tier

In this app, I simulate a real-life scenario of a honey factory: receiving orders for different honey types (acacia, linden, etc.) and packaging sizes (200 ml, 400 ml, or 800 ml jars).
I used JDBC to connect to multiple PostgreSQL RDS instances (for jars, labels, crates, and per-honey-type stocks).
Each connection to an RDS instance was managed by a virtual thread.
I applied structured concurrency because if any connection fails to retrieve data from RDS, the order cannot be delivered.
After a successful delivery, I persisted the order records in Amazon DynamoDB.