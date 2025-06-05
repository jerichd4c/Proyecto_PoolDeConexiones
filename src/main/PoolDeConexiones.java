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
    private int hilosActivos=0; 
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
        timeout = Integer.parseInt(configPool.getProperty("timeout"));
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
    private Connection crearConexionFisica() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    //metodo para obtener una conexion del pool
    public synchronized Connection getConnection() throws SQLException {
        //se ejecuta el metodo, hay 1 hilo activo
    try {
        incrementarHilosActivos();

    // connection timeout: variable local para controlar el tiempo de espera de la conexion
        long tiempoInicio= System.currentTimeMillis();

        //implementacion del nuevo metodo -> QUITAR // si se quiere activar
        //final long timeout = calcularTimeout();

        while (conexionesDisponibles.isEmpty()) {

        //si no hay conexiones disponibles usa el metodo para crecer el pool usando incrementoConex
        if (conexionesDisponibles.isEmpty() && conexionesTotales() < maxConexiones) {
        crecerPool();
        }

        //si no hay conexiones disponibles, espera en bloques pequeños
        try {
            wait(100); 

            // connection timeout: cuando el tiempo inicial supere el tiempo de espera establecido
            if (System.currentTimeMillis() - tiempoInicio > timeout) {
            throw new SQLException("Connection timeout.");

            }            
        } catch (InterruptedException e) {
            //si se interrumpe la espera, saldra un mensaje de error que se interrumpio el metodo
            Thread.currentThread().interrupt();
            throw new SQLException("Error al esperar la conexion disponible: " + timeout);
            }
        }

        //remueve la primera conexion disponible de la linkedList de conexiones disponibles y la agrega a la linkedList de conexiones usadas
        Connection conn = conexionesDisponibles.removeFirst();
        conexionesUsadas.add(conn);
        //crea una nuevo conexion disponible
        return new PooledConnection(conn, this);
    } finally {
        decrementarHilosActivos();
        }
    }

    //metodo para devolver conexiones fisica al pool (usado en PooledConnection)
    void devolverConexionFisica(Connection conexionFisica) {
        synchronized (this) {
            //si se remueve un elemento de la lista de conexiones usadas, se agarra la conex de SQL y se devuelve al pool (conex disponibles)
            if (conexionesUsadas.remove(conexionFisica)) {
                conexionesDisponibles.addLast(conexionFisica);
                //se le notificara a los demas hilos para que puedan agarrar del pool
                notifyAll();
            }
    
        }

    }

    //metodo para ejecutar querys
    public synchronized void ejecutarQuery(String query) throws SQLException {
       try (Connection conn = getConnection();
        Statement stmt = conn.createStatement()) { 
        stmt.execute(query);
        }
    }

    //metodo para verificar el total de conexiones de un pool (auxiliar)
    private synchronized int conexionesTotales() {
        //sumara la cantidad de conexiones disponibles y usadas, para asi tener el total del pool
        return conexionesDisponibles.size() + conexionesUsadas.size();
    }

    //metodo para crecer un pool de conexiones (auxiliar)
    public synchronized void crecerPool() throws SQLException {
        //metodo Math.min tomara el menor valor entre incConex y la resta y ese sera el valor de crecimiento
        //caso 1: incConex=5 y resta de las dos var 100 - 10 = 90, min(5, 90) = 5 crecimiento
        //caso 2: incConex=5 y resta de las dos var 100 - 97 = 3, min(5, 3) = 3 crecimiento
        int crecimiento = Math.min(incrementoConex, maxConexiones - conexionesTotales());
        for (int i=0 ; i<crecimiento ; i++) {
            conexionesDisponibles.add(crearConexionFisica());
        }
    }

    //metodos relacionados a el control del timeout de consultas:

    //metodo para incrementar hilos activos
    private synchronized void incrementarHilosActivos() {
        //se le sumara 1 a la variable de hilosActivos, es decir, por cada getConn se supondra que hay un hilo activo
        hilosActivos++;
    }

    //metodo para decrementar hilos activos
    private synchronized void decrementarHilosActivos() {
        //se le restara 1 a la variable de hilosActivos, el hilo termino su ejecucion
        hilosActivos--;
    }   

    //metodo para retornar hilos activos
    private synchronized int getHilosActivos() {
        return hilosActivos;
    }

    //metodo final para calcular timeout
    //NT: metodo experimental
    // private synchronized long calcularTimeout() {
    //     int conexTotales=getConexionesTotales();
    //     int conexUsadas=getConexionesUsadasSize();
    //     int conexActivas=getHilosActivos();
    //     //calcular factores del timeout
    //     //diferencia entre conexiones usadas y total de conexiones
    //     //NT: usado para calcular la carga actual (la que se hace ANTES de la prueba)
    //     double factorDeCarga = (double) conexUsadas / Math.max(1, conexTotales);
    //     //diferencia entre conexiones activas y conexiones maximas (limite del pool)
    //     //NT: usado para calcular la presion que se esta ejerciendo DURANTE la prueba
    //     double factorDePresion= (double) conexActivas / Math.max(1, maxConexiones);
    //     //diferencia entre conexiones max y conex totales entre conec max 
    //     //NT: usado para calcular la capacidad de crecimiento que puede tener el timeout
    //     double factordeCrecimiento= (double) (maxConexiones - conexTotales) / Math.max(1, maxConexiones);
        
    //     //calcular timeout tomadno en cuenta todos los factores
    //     return timeout + (long) (timeout * factorDeCarga) + (long) (timeout * factorDePresion) + (long) (timeout * factordeCrecimiento);
    // }

    //metodos getters axuliares para otras clases

    //retornar el tamaño de la lista de conex usadas
    public int getConexionesUsadasSize() {
    return conexionesUsadas.size();
}
    //retornar el total de conexiones del pool
    public int getConexionesTotales() {
    return conexionesTotales();
}
}    