import java.sql.*;

public class PoolManager{
    //extiende implicitamente de PoolDeConexiones
    private final PoolDeConexiones pool;
    
    //constructor
    public PoolManager() {
        //crea la instancia del pool
        this.pool = PoolDeConexiones.getInstance();
    }

    //metodo para inicializar la conexion con la instancia del pool
    public void crearPool () throws SQLException { 
        pool.cargarConfig();
    }

    //metodo para obtener una conexion del pool 
    public Connection getConnection() throws SQLException {
        //debugger:
        //System.out.println ("Conexiones disponibles: " + ( pool.getConexionesTotales() - pool.getConexionesUsadasSize()));
        return pool.getConnection();
    }

    //metodo para devolver la conexion al pool 
    public void returnConnectiontoPool( Connection conn )  {
        pool.devolverConexion(conn);
    }

    //auxiliar: metodo para devolver pool de conexiones (verificacion de instancias)
     public PoolDeConexiones getPool() {
        return this.pool;
    }

}