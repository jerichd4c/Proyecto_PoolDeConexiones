import java.io.*;
import java.sql.*;
import java.util.*;

public class PruebaDeEstres {

    //datos de MI posgreSQL
    private static String url;
    private static  String user;
    private static  String password;

    //variables de control para hilos
    private static int NUM_CONEXIONES;
    private static int hilosProcesados = 0;
    private static int hilosPerdidos = 0;
    private static int hilosImpresos= 0;
    private static long tiempoInicial;
    private static long tiempoFinal;

    //nuevas variables: poolManager y pooledConnection

    private static PoolManager poolManager;

    public static void main(String[] args) {   

    //inicializar el poolManager

    poolManager = new PoolManager(); 
    try {
        poolManager.crearPool(); 
    } catch (SQLException e) {
        System.out.println("Error al carga la configuracion del pool: " + e.getMessage());
        System.exit(1);
    }

    java.util.Scanner mainScanner = new java.util.Scanner(System.in);

    cargarBBD();
    verificarConexion();
    asignarNUM_CONEXIONES(mainScanner);
    //ya el scanner no se usara mas, se cierra para evitar filtramiento de recursos
    mainScanner.close();

    //probar PoolDeConexiones singleton
    probarInstancia();

    crearTablaPrueba();
    //hacer despues de crear tabla para que haga (select * from tabla) de una
    tiempoInicial = System.currentTimeMillis(); 
    pruebaDeEstres();      
    mostrarResumen();

}

//metodos usados (en orden)

    //metodo para cargar variables directamente desde configSQL.properties
    private static void cargarBBD() {

        Properties config = new Properties();
        try (InputStream input = PruebaDeEstres.class.getResourceAsStream("resources/configSQL.properties")) {
            config.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar la configuracion del pool de conexiones: " + e.getMessage());
        }
        url = config.getProperty("url");
        user = config.getProperty("user");
        password = config.getProperty("password");
    }
    
    //metodo para verificar la conexion con la base de datos
    private static void verificarConexion() {
        try (Connection con = DriverManager.getConnection(url, user, password)) {
        if (con != null) {
            System.out.println("Conexion exitosa, base de datos cargada exitosamente");
        } else {
            System.out.println("Conexion fallida");
            System.exit(1);
            }
        } catch (SQLException e) {
    System.out.println("Error: " + e.getMessage());
        System.exit(1);
        }
    }

    //metodo para crear tabla y registros (5000)
    private static void crearTablaPrueba() {

        try {
            PoolDeConexiones pool = PoolDeConexiones.getInstance();
            pool.ejecutarQuery("DROP TABLE IF EXISTS tabla ");
            pool.ejecutarQuery("CREATE TABLE tabla (" + "id SERIAL PRIMARY KEY, " + "descript VARCHAR(10000))");
                //crear 5000 registros NO CONFUNDIR CON NUM_CONEXIONES YA QUE SI SE USA SE PUEDEN CREAR MAS REGISTROS DE LO HABITUAL PLS
                //texto de ejemplo:
            String texto = "a".repeat(10000);
            for (int i=0; i<5000; i++) {
                pool.ejecutarQuery("INSERT INTO tabla (descript) VALUES ('" + texto + "')");
            }
            System.out.println("Tabla creada exitosamente");
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    //metodo para asignar numero a NUM_CONEXIONES
    private static void asignarNUM_CONEXIONES(java.util.Scanner sc) {
        try {
            System.out.println("Indique el numero de consultas quiere realizar: ");
            NUM_CONEXIONES = sc.nextInt(); 
            sc.close();
        } catch (Exception e) {
            System.out.println("Error: Introduzca un valor entero");
            System.exit(1);
        }
    }

    //metodo para verificar que PoolDeConexiones sea una unica instancia
    //verifica el hashcode ( de objeto)
    private static void probarInstancia() {

        //verificar que no se repita instancia de pool 

    //pool 1

    PoolDeConexiones pool1 = PoolDeConexiones.getInstance();
    System.out.println("pool 1: " + pool1.hashCode());

    //pool 2

    PoolDeConexiones pool2 = PoolDeConexiones.getInstance();
    System.out.println("pool 2: " + pool2.hashCode());

    //pool 3

    PoolDeConexiones pool3 = PoolDeConexiones.getInstance();
    System.out.println("pool 3: " + pool3.hashCode());


    //se comparan (es verificado por el hashcode)

    if (pool1 == pool2 && pool2 == pool3) {
        System.out.println("Las instancias del pool son iguales");
    } else {
        System.err.println("Las instancias del pool son diferentes (no es SINGLETON)");
    }

    //creacion de pool manager

    PoolManager manager1 = new PoolManager();
    PoolManager manager2 = new PoolManager();
    PoolManager manager3 = new PoolManager();

    System.out.println("manager1 apuntando a: " + manager1.getPool().hashCode());
    System.out.println("manager2 apuntando a: " + manager2.getPool().hashCode());
    System.out.println("manager3 apuntando a: " + manager3.getPool().hashCode());

    if (manager1.getPool() == pool1 && manager2.getPool() == pool1 && manager3.getPool() == pool1) {
        System.out.println("Las instancias del pool manager son iguales");
    } else {
        System.err.println("Las instancias del pool manager son diferentes (no es SINGLETON)");
    }

    }

    //metodo para ejecutar prueba de estres 
    private static void pruebaDeEstres() {
    //NT: cambiar este metodo mas tarde
        for (int i=0; i<NUM_CONEXIONES; i++) {
            //la variable ID se le sumara +1 despues de cada FOR loop y este se printea al final del metodo
            final int ID=i+1;
            new Thread(() -> {

                float inicio = System.currentTimeMillis() - tiempoInicial;
                String estado=" ";
                
                try (Connection con = poolManager.getConnection();
                    //createStatemen llama al metodo sobreescrito de la clase PooledConnection
                    Statement stmt = con.createStatement(); 
                    //variable que almacena la consulta SQL en forma de array (select)
                    ResultSet rs= stmt.executeQuery("SELECT * FROM tabla")) {

                    //vacio para simular carga real mientras se mueve por el array
                    while (rs.next()) {}

                    synchronized (PruebaDeEstres.class) {
                        hilosProcesados++;
                        estado="Hilo procesado";

                    } 
                } catch (SQLException e) {
                    synchronized (PruebaDeEstres.class) {
                        hilosPerdidos++;
                        //mensaje que muestra el error especifico (e.getMessage())
                        estado="Hilo perdido: " + e.getMessage();
                    }
                    
                // al final del try-catch se llama automaticamente a close(), sin embargo como el metodo 
                // estara sobreescrito esta ira al final de la lista de conexDisponibles

                } finally {

                    //NT: currentTimeMillis() agarra el tiempo del reloj, hacer diferencia=
                    float fin= System.currentTimeMillis() - tiempoInicial;
                    synchronized (System.out) {
                        System.out.printf("Hilo: %d / Inicio: %f segs / Fin: %f segs / Estado: %s%n", ID, inicio/1000, fin/1000, estado);
                        hilosImpresos++;
                        if (hilosImpresos == NUM_CONEXIONES) {
                            //proceeso termino, se le notificara a la consola que ya se imprimeron los HILOS, para que el resumen salga de ultimo
                            System.out.notifyAll(); 
                        }

                    }
                    
                }
            }).start();
        }
    }

    //metodo para mostrar resumen
    private static void mostrarResumen() {
    
        synchronized (System.out) {
        while (hilosImpresos < NUM_CONEXIONES) {
            try {
                //necesario para que el resumen no se pierda entre status de hilos (main thread va a esperar)
                System.out.wait();
            } catch (InterruptedException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    tiempoFinal = System.currentTimeMillis(); 

    //no confundir con variable fin, esta es la duracion de TODA la prueba, fin es de hilo c/u
    double duracionTotal = (tiempoFinal - tiempoInicial)/1000;
         
    System.out.println("Hilos procesados: " + hilosProcesados);
    System.out.println("Hilos perdidos: " + hilosPerdidos);
    System.out.println("Total de hilos: " + (hilosProcesados + hilosPerdidos));
    System.out.printf("Duracion total de la prueba: %f segs", duracionTotal);

        }
    }

}