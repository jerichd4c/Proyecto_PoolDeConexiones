import java.sql.*;

public class PoolManager{
    private final PoolDeConexiones pool;

    public PoolManager() {
        //retorna la instancia del pool de conexiones para verificar si existe (metodo de PoolDeConexiones)
        this.pool = PoolDeConexiones.getInstance();
    }

    //metodo para inicializar la conexion con la instancia del pool
    public void crearPool () throws SQLException { 
        pool.cargarConfig();
    }

    //metodo para obtener una conexion del pool 
    public Connection getConnection() throws SQLException {
        System.out.println ("Conexiones disponibles: " + ( pool.getConexionesTotales() - pool.getConexionesUsadasSize()));
        return pool.getConnection();
    }

    //metodo para devolver la conexion al pool
    public void devolverConexionPool (Connection conn) {
        //instanceof booleano que verifica si la conexion es una instancia de PooledConnection
        if (conn instanceof PooledConnection) {
            try {
                //cierra la conexion 
                conn.close();
            } catch (SQLException e) {
                System.out.println("Error al devolver la conexion al pool: " + e.getMessage());
            }
        }
    }

    //metodo para agregar una conexion al pool 
    public void agregarConexionPool() throws SQLException {
        pool.crecerPool();
    }

}