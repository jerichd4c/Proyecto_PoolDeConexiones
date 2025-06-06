import java.io.*;
import java.sql.*;
import java.util.*;

public class PoolDeConexiones { 

    //variable que evalua si el pool de conexiones ya existe y evitar creacion de multiples instancias
    private static PoolDeConexiones instance;
    private String url;
    private String user;
    private String password;
    //variables de crecimiento del pool
    private int maxConexiones;
    private int minConexiones;
    private int incrementoConex;
    //variables que controlan el timeout
    private int timeout;
    //variables para llevar control de las conexiones disponibles y usadas
    private final LinkedList<Connection> conexionesDisponibles = new LinkedList<>();
    private final LinkedList<Connection> conexionesUsadas = new LinkedList<>();

    //constructor que asegura que solo se crea una UNICA instancia del pool de conexiones entre las clases 
    private PoolDeConexiones() {
        //verificar que solo se tenga una SOLA instancia del pool de conexiones en el proyecto
        if (instance != null) {
            throw new IllegalStateException("Ya existe una instancia del pool de conexiones.");
        }
    }

    //metodo para retornar la instancia del pool
    public static synchronized PoolDeConexiones getInstance() {
        if (instance == null) {
            //si no existe ninguna instancia del pool, la crea
            instance = new PoolDeConexiones();
        }
        return instance;
    }

    //metodo para cargar configuracion del pool de conexiones
    public synchronized void cargarConfig() throws SQLException {
        if (!conexionesDisponibles.isEmpty()) {
            //si la lista no esta vacia, retorna el pool
            return;
        }
        //cargar configuracion del pool de conexiones
        Properties configPool = new Properties();
        try (InputStream input = getClass().getResourceAsStream("resources/configSQL.properties")) {
            configPool.load(input);
        } catch (Exception e) {
            throw new SQLException("Error al cargar la configuracion del pool de conexiones: " + e.getMessage());
        }
        maxConexiones = Integer.parseInt(configPool.getProperty("maxConexiones"));
        minConexiones = Integer.parseInt(configPool.getProperty("minConexiones"));
        incrementoConex = Integer.parseInt(configPool.getProperty("incrementoConex"));
        //timeout = Integer.parseInt(configPool.getProperty("timeout"));
        url = configPool.getProperty("url");
        user = configPool.getProperty("user");
        password = configPool.getProperty("password");
        
        //crear conexiones iniciales del pool usando configSQL.properties
        for (int i = 0; i < minConexiones; i++) {
            conexionesDisponibles.add(crearConexionFisica());
        }
        System.out.println("Pool de conexiones cargado exitosamente");
    }

    //metodo para crear una nueva conexion (la conexion fisica que se conecta al SQL local)
    //es decir, metodo para crear 1 sola conex, se puede usar para hacer crecer conexDisponibles y conexUsadas
    private Connection crearConexionFisica() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    //metodo para obtener una conexion del pool
    public synchronized Connection getConnection() throws SQLException {

    // connection timeout: variable local para controlar el tiempo de espera de la conexion
        
        //bucle while que se hace cuando ya no hay conexiones disponibles
        while (conexionesDisponibles.isEmpty()) {

        //caso 1: si no hay conexiones disponibles usa el metodo para crecer el pool usando crecerPool
        if (conexionesDisponibles.isEmpty() && conexionesTotales() < maxConexiones) {
        crecerPool();
        }

        //si no hay conexiones disponibles, espera en bloques pequeños
        try {
            //esperar hasta encontrar otra conexion disponible
            wait(100);

        } catch (InterruptedException e) {
            //si se interrumpe la espera, saldra un mensaje de error que se interrumpio el metodo
            Thread.currentThread().interrupt();
            throw new SQLException("Error al esperar la conexion disponible: " + timeout);
            }
        }
        
        //referencia al hilo
        synchronized (this) {
        //remueve la primera conexion disponible de la linkedList de conexiones disponibles y la agrega a la linkedList de conexiones usadas
        Connection conn = conexionesDisponibles.removeFirst();
        //lo agrega a la lista de conexiones que se usaron
        conexionesUsadas.add(conn);
        //crea una nuevo conexion disponible
        return conn;
        }
    } 

    //metodo para ejecutar querys
    public synchronized void ejecutarQuery(String query) throws SQLException {
       try (Connection conn = getConnection();
        Statement stmt = conn.createStatement()) { 
        stmt.execute(query);
        }
    }

    //metodo para crecer un pool de conexiones 
    public synchronized void crecerPool() throws SQLException {
        int crecimiento = incrementoConex;
        for (int i=0 ; i<crecimiento ; i++) {
            conexionesDisponibles.add(crearConexionFisica());
        }
    }

    //metodo para devolver conexion al pool (anteriormente de pooledConnection)
    public void devolverConexion(Connection conn) {
        synchronized (this) {
            //si se remueve la conexion, se añadira a la lista enlazada de conexiones disponibles
            if (conexionesUsadas.remove(conn)) {
                try {
                    // isClosed SIN override
                    if (conn.isClosed()) {
                        conexionesDisponibles.add(crearConexionFisica());
                    } else {
                        conexionesDisponibles.add(conn);
                    }
                } catch (SQLException e) {
                    //si SQL arroja error, intentar de todas formas
                    try {
                        conexionesDisponibles.add(crearConexionFisica());
                    } catch (SQLException ex) {
                        //si no se puede crear una nueva conexion despues de intentar 3 metodos, error definitivo
                        System.err.println("Error: " + e.getMessage());
                    }
                    notifyAll();
                }
            }
        }
    }

    //metodos getters axuliares:

    //metodo para verificar el total de conexiones de un pool (auxiliar)
    private synchronized int conexionesTotales() {
        //sumara la cantidad de conexiones disponibles y usadas, para asi tener el total del pool
        return conexionesDisponibles.size() + conexionesUsadas.size();
    }

    //retornar el tamaño de la lista de conex usadas
    public int getConexionesUsadasSize() {
    return conexionesUsadas.size();
}
    //retornar el total de conexiones del pool
    public int getConexionesTotales() {
    return conexionesTotales();
}
}    