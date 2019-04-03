## Module overview

This module provides the functionality required to access and manipulate data stored in an H2 database. 

### Client

To access a database, you must first create a `client` object. Create a client of the H2 Client type (i.e., `h2:Client`) and provide the necessary connection parameters.

### Connection pool handling

There are 3 possible scenarios of connection pool handling

1. If you do not provide the `poolOptions` field, a globally shareable pool will be created for your database if a connection pool matching with the properties you provided
doesn't exist already.

```ballerina
h2:Client testDB = new({
    path: "/home/ballerina/test/",
    name: "testdb",
    username: "SA",
    password: ""
});
```

2. If you define the `poolOptions` field inline, an unshareable connection pool will be created.

```ballerina
h2:Client testDB = new({
    path: "/home/ballerina/test/",
    name: "testdb",
    username: "SA",
    password: "",
    poolOptions: { maximumPoolSize: 5 }
});
```

3. If you create a record of type `sql:PoolOptions` and reuse that in the configuration of multiple clients, for each
set of clients that connect to the same database instance with the same set of properties, a shared connection pool
will be created.

```ballerina
h2:Client testDB1;
h2:Client testDB2;
h2:Client testDB3;

sql:PoolOptions poolOptions1 = { maximumPoolSize: 5 };

testDB1 = new({
    path: "/home/ballerina/test",
    name: "testdb1",
    username: "SA",
    password: "",
    poolOptions: poolOptions1
});

testDB2 = new({
    path: "/home/ballerina/test",
    name: "testdb2",
    username: "SA",
    password: "",
    poolOptions: poolOptions1
});

testDB3 = new({
    path: "/home/ballerina/test",
    name: "testdb1",
    username: "SA",
    password: "",
    poolOptions: poolOptions1
});
```

The default values for the connection pool properties can be found in the `sql:PoolOptions` type.

### Database operations

Once the client is created, database operations can be executed through that client. This module provides support for creating tables and executing stored procedures. It also supports selecting, inserting, deleting, updating, and batch updating data. For more details on the supported remote functions refer to the `sql` module. Also the details of the SQL data types and query parameters relevant to these database operations can be found in the documentation of the `sql` module.

## Samples

### Creating a client in H2 Embedded Mode

```ballerina
h2:Client testDB = new({
    path: "/home/ballerina/test/",
    name: "testdb",
    username: "SA",
    password: ""
});
```

### Creating a client in H2 Server Mode

```ballerina
h2:Client testDB = new({
    host: "localhost",
    port: 9092,
    name: "testdb",
    username: "SA",
    password: ""
});
```

### Creating a client in H2 In-Memory Mode

```ballerina
h2:Client testDB = new({
    name: "testdb",
    username: "SA",
    password: ""
});
```

The full list of client properties can be found in the `sql:PoolOptions` type.
