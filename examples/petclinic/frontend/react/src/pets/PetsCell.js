import React from 'react';
import config from "../config";
import {Link} from "react-router-dom";

const PetsCell = ({pet, match}) =>
  <div className="col-sm" style={{
    backgroundImage: `url(${config.SERVER_URL}/images/${pet.image})`,
    backgroundPosition: 'center',
    backgroundRepeat: 'no-repeat',
    backgroundSize: 'cover',
    margin: '10px',
    minHeight: '300px'
  }}>
    <Link style={{position: 'absolute', top: 0, left: 0, height: '100%', width: '100%'}} to={`${match.url}/${pet.slug}`}>
      <div style={{
        position: 'absolute',
        color: 'white',
        bottom: 0,
        left: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        padding: '10px',
        width: '100%'
      }}>
        <h4>
          {pet.name}
        </h4>

      </div>
    </Link>
  </div>

export default PetsCell;
